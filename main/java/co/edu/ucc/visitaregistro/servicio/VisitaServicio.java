package co.edu.ucc.visitaregistro.servicio;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.modelo.Modelos.Anfitrion;
import co.edu.ucc.visitaregistro.modelo.Modelos.Area;
import co.edu.ucc.visitaregistro.modelo.Modelos.EstadoVisita;
import co.edu.ucc.visitaregistro.modelo.Modelos.Visita;
import co.edu.ucc.visitaregistro.modelo.Modelos.Visitante;
import co.edu.ucc.visitaregistro.repositorio.CatalogoRepositorio;
import co.edu.ucc.visitaregistro.repositorio.VisitaRepositorio;
import co.edu.ucc.visitaregistro.repositorio.VisitaRepositorio.Filtro;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;
import co.edu.ucc.visitaregistro.servicio.Vistas.ActivasVista;
import co.edu.ucc.visitaregistro.servicio.Vistas.AnfitrionOpcion;
import co.edu.ucc.visitaregistro.servicio.Vistas.CatalogosVista;
import co.edu.ucc.visitaregistro.servicio.Vistas.HistorialVista;
import co.edu.ucc.visitaregistro.servicio.Vistas.Opcion;
import co.edu.ucc.visitaregistro.servicio.Vistas.PanelVista;
import co.edu.ucc.visitaregistro.servicio.Vistas.VisitaVista;

import java.sql.SQLException;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.OptionalDouble;

/**
 * Lógica de las visitas: registrar ingreso (RF-01, RF-02, RF-03), registrar
 * salida y duración (RF-04), buscar (RF-05), visitas activas (RF-06) y el panel.
 */
public class VisitaServicio {

    public static final List<String> MOTIVOS = List.of(
        "Reunión de proyecto", "Reunión comercial", "Entrega", "Entrevista", "Asesoría", "Otro"
    );
    private static final int LIMITE_HISTORIAL = 200;
    private static final int MINUTOS_VISITA_PROLONGADA = 240;

    public record SolicitudVisita(
        String nombre, String documento, String empresa, String motivo, Integer anfitrionId, Integer areaId) {
    }

    public record FiltroHistorial(
        String desde, String hasta, String texto, Integer anfitrionId, Integer areaId, String estado) {
    }

    private final BaseDatos bd;
    private final Permisos permisos;
    private final VisitaRepositorio visitas = new VisitaRepositorio();
    private final CatalogoRepositorio catalogo = new CatalogoRepositorio();

    public VisitaServicio(BaseDatos bd, Permisos permisos) {
        this.bd = bd;
        this.permisos = permisos;
    }

    public CatalogosVista catalogos() throws SQLException {
        return bd.consultar(c -> new CatalogosVista(
            catalogo.areasActivas(c).stream().map(a -> new Opcion(a.id(), a.nombre())).toList(),
            catalogo.anfitrionesActivos(c).stream()
                .map(a -> new AnfitrionOpcion(a.id(), a.nombre(), a.areaId(), a.areaNombre())).toList(),
            MOTIVOS
        ));
    }

    /** CU-01 y CU-02: registra el ingreso con la hora del servidor. */
    public VisitaVista registrar(Sesion s, SolicitudVisita solicitud) throws SQLException {
        permisos.exigirRegistrar(s);

        String nombre = Validacion.limpiar(solicitud.nombre());
        String documento = Validacion.limpiar(solicitud.documento());
        documento = documento == null ? null : documento.toUpperCase(Locale.ROOT);
        String empresa = Validacion.limpiar(solicitud.empresa());
        String motivo = Validacion.limpiar(solicitud.motivo());

        Validacion v = new Validacion()
            .nombrePersona("nombre", nombre)
            .documento("documento", documento)
            .maximo("empresa", empresa, 100)
            .requerido("motivo", motivo)
            .requerido("anfitrionId", solicitud.anfitrionId())
            .requerido("areaId", solicitud.areaId());
        if (motivo != null) {
            v.cumple("motivo", MOTIVOS.contains(motivo), "Selecciona un motivo de la lista.");
        }
        final String documentoFinal = documento;

        return bd.transaccion(c -> {
            if (solicitud.anfitrionId() != null) {
                Anfitrion anfitrion = catalogo.anfitrion(c, solicitud.anfitrionId());
                v.cumple("anfitrionId", anfitrion != null && anfitrion.activo(), "Selecciona un anfitrión de la lista.");
            }
            if (solicitud.areaId() != null) {
                Area area = catalogo.area(c, solicitud.areaId());
                v.cumple("areaId", area != null && area.activa(), "Selecciona un área de la lista.");
            }
            v.verificar();

            if (documentoFinal != null && visitas.tieneVisitaActiva(c, documentoFinal)) {
                throw Errores.conflicto("Esta persona ya tiene una visita activa. Registra su salida antes de un nuevo ingreso.");
            }

            LocalDateTime ahora = permisos.ahora();
            int visitanteId;
            Visitante existente = documentoFinal == null ? null : visitas.visitantePorDocumento(c, documentoFinal);
            if (existente != null) {
                visitanteId = existente.id();
                visitas.actualizarVisitante(c, visitanteId, nombre, empresa);
            } else {
                visitanteId = visitas.insertarVisitante(c, nombre, documentoFinal, empresa, ahora);
            }

            int id = visitas.insertar(c, visitanteId, solicitud.anfitrionId(), solicitud.areaId(), motivo, ahora, s.usuarioId());
            return vista(s, visitas.porId(c, id), ahora);
        });
    }

    /** CU-03: registra la salida, calcula la duración y finaliza la visita. */
    public VisitaVista registrarSalida(Sesion s, int id) throws SQLException {
        permisos.exigirRegistrar(s);
        return bd.transaccion(c -> {
            Visita visita = visitas.porId(c, id);
            if (visita == null) {
                throw Errores.noEncontrado("La visita no existe.");
            }
            if (!visita.activa()) {
                throw Errores.conflicto("Esta visita ya tiene la salida registrada.");
            }
            LocalDateTime ahora = permisos.ahora();
            LocalDateTime salida = ahora.isBefore(visita.ingreso()) ? visita.ingreso() : ahora;
            int minutos = (int) Duration.between(visita.ingreso(), salida).toMinutes();
            if (!visitas.registrarSalida(c, id, salida, minutos, s.usuarioId())) {
                throw Errores.conflicto("Esta visita ya tiene la salida registrada.");
            }
            return vista(s, visitas.porId(c, id), ahora);
        });
    }

    /** CU-04: visitas que todavía no registran salida. */
    public ActivasVista activas(Sesion s, String texto) throws SQLException {
        LocalDateTime ahora = permisos.ahora();
        List<Visita> lista = bd.consultar(c -> visitas.buscar(c, new Filtro(
            null, null, Validacion.limpiar(texto), permisos.anfitrionObligatorio(s), null, EstadoVisita.ACTIVA, 500)));
        return new ActivasVista(ahora, lista.stream().map(v -> vista(s, v, ahora)).toList());
    }

    /** CU-05 y CU-08: búsqueda protegida según el rol. */
    public HistorialVista historial(Sesion s, FiltroHistorial f) throws SQLException {
        Validacion v = new Validacion();
        LocalDate desde = fecha(f.desde(), "desde", v);
        LocalDate hasta = fecha(f.hasta(), "hasta", v);
        if (desde != null && hasta != null) {
            v.cumple("hasta", !hasta.isBefore(desde), "La fecha final debe ser igual o posterior a la inicial.");
        }
        EstadoVisita estado = null;
        if (f.estado() != null && !f.estado().isBlank() && !f.estado().equalsIgnoreCase("TODOS")) {
            try {
                estado = EstadoVisita.valueOf(f.estado().trim().toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                v.cumple("estado", false, "Selecciona un estado de la lista.");
            }
        }
        v.verificar();

        LocalDate permitido = permisos.inicioPermitido(s).toLocalDate();
        boolean ajustado = desde != null && desde.isBefore(permitido);
        LocalDate desdeAplicado = desde == null || ajustado ? permitido : desde;
        LocalDate hastaAplicado = hasta == null ? permisos.ahora().toLocalDate() : hasta;

        Integer anfitrion = permisos.anfitrionObligatorio(s);
        Filtro filtro = new Filtro(
            desdeAplicado.atStartOfDay(),
            hastaAplicado.plusDays(1).atStartOfDay(),
            Validacion.limpiar(f.texto()),
            anfitrion != null ? anfitrion : f.anfitrionId(),
            f.areaId(),
            estado,
            LIMITE_HISTORIAL + 1
        );
        LocalDateTime ahora = permisos.ahora();
        List<Visita> encontradas = bd.consultar(c -> visitas.buscar(c, filtro));
        boolean truncado = encontradas.size() > LIMITE_HISTORIAL;
        List<VisitaVista> resultado = encontradas.stream().limit(LIMITE_HISTORIAL).map(x -> vista(s, x, ahora)).toList();
        return new HistorialVista(resultado, truncado, LIMITE_HISTORIAL, desdeAplicado, hastaAplicado,
            permisos.diasDeHistorial(s), ajustado);
    }

    public VisitaVista detalle(Sesion s, int id) throws SQLException {
        Visita visita = bd.consultar(c -> visitas.porId(c, id));
        if (visita == null || !puedeVer(s, visita)) {
            throw Errores.noEncontrado("La visita no existe o no tienes permiso para verla.");
        }
        return vista(s, visita, permisos.ahora());
    }

    /** Datos del panel principal, limitados a lo que el rol puede ver. */
    public PanelVista panel(Sesion s) throws SQLException {
        LocalDateTime ahora = permisos.ahora();
        LocalDateTime inicioHoy = ahora.toLocalDate().atStartOfDay();
        LocalDateTime inicioAyer = inicioHoy.minusDays(1);
        Integer anfitrion = permisos.anfitrionObligatorio(s);

        return bd.consultar(c -> {
            List<Visita> activas = visitas.buscar(c, new Filtro(null, null, null, anfitrion, null, EstadoVisita.ACTIVA, 5000));
            List<Visita> recientes = visitas.buscar(c, new Filtro(inicioAyer, null, null, anfitrion, null, null, 5000));
            List<Visita> ultimas = visitas.buscar(c, new Filtro(permisos.inicioPermitido(s), null, null, anfitrion, null, null, 5));

            int ultimaHora = (int) recientes.stream().filter(v -> !v.ingreso().isBefore(ahora.minusHours(1))).count();
            int entradasHoy = (int) recientes.stream().filter(v -> !v.ingreso().isBefore(inicioHoy)).count();
            int entradasAyer = (int) recientes.stream().filter(v -> v.ingreso().isBefore(inicioHoy)).count();
            List<Visita> finalizadasHoy = recientes.stream()
                .filter(v -> v.salida() != null && !v.salida().isBefore(inicioHoy)).toList();
            List<Visita> finalizadasAyer = recientes.stream()
                .filter(v -> v.salida() != null && v.salida().isBefore(inicioHoy)).toList();
            int prolongadas = (int) activas.stream()
                .filter(v -> Duration.between(v.ingreso(), ahora).toMinutes() >= MINUTOS_VISITA_PROLONGADA).count();

            return new PanelVista(
                ahora,
                activas.size(),
                ultimaHora,
                entradasHoy,
                entradasAyer,
                finalizadasHoy.size(),
                promedio(finalizadasHoy),
                promedio(finalizadasAyer),
                prolongadas,
                ultimas.stream().map(v -> vista(s, v, ahora)).toList()
            );
        });
    }

    VisitaVista vista(Sesion s, Visita v, LocalDateTime ahora) {
        boolean verDocumento = permisos.puedeVerDocumento(s);
        Integer transcurridos = v.activa() ? (int) Math.max(0, Duration.between(v.ingreso(), ahora).toMinutes()) : null;
        return new VisitaVista(
            v.id(),
            v.visitanteNombre(),
            verDocumento ? v.documento() : null,
            !verDocumento && v.documento() != null,
            v.empresa(),
            v.anfitrionId(),
            v.anfitrionNombre(),
            v.areaId(),
            v.areaNombre(),
            v.motivo(),
            v.ingreso(),
            v.salida(),
            v.duracionMinutos(),
            transcurridos,
            v.estado().name(),
            v.registradoPor()
        );
    }

    private boolean puedeVer(Sesion s, Visita v) {
        Integer anfitrion = permisos.anfitrionObligatorio(s);
        if (anfitrion != null && anfitrion != v.anfitrionId()) {
            return false;
        }
        return v.activa() || !v.ingreso().isBefore(permisos.inicioPermitido(s));
    }

    private static Integer promedio(List<Visita> lista) {
        OptionalDouble media = lista.stream()
            .filter(v -> v.duracionMinutos() != null)
            .mapToInt(Visita::duracionMinutos)
            .average();
        return media.isPresent() ? (int) Math.round(media.getAsDouble()) : null;
    }

    private static LocalDate fecha(String texto, String campo, Validacion v) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(texto.trim());
        } catch (DateTimeParseException e) {
            v.cumple(campo, false, "Escribe una fecha válida.");
            return null;
        }
    }
}
