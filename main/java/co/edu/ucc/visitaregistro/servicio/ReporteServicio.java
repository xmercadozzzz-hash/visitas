package co.edu.ucc.visitaregistro.servicio;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.modelo.Modelos.Area;
import co.edu.ucc.visitaregistro.modelo.Modelos.Visita;
import co.edu.ucc.visitaregistro.repositorio.CatalogoRepositorio;
import co.edu.ucc.visitaregistro.repositorio.VisitaRepositorio;
import co.edu.ucc.visitaregistro.repositorio.VisitaRepositorio.Filtro;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;
import co.edu.ucc.visitaregistro.servicio.Vistas.AreaResumen;
import co.edu.ucc.visitaregistro.servicio.Vistas.BarraVista;
import co.edu.ucc.visitaregistro.servicio.Vistas.ReporteVista;

import java.sql.SQLException;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/** RF-07 y CU-06: reportes de visitas por día y por área. */
public class ReporteServicio {

    private static final String[] DIAS = {"Lun", "Mar", "Mié", "Jue", "Vie", "Sáb", "Dom"};

    private final BaseDatos bd;
    private final Permisos permisos;
    private final VisitaRepositorio visitas = new VisitaRepositorio();
    private final CatalogoRepositorio catalogo = new CatalogoRepositorio();

    public ReporteServicio(BaseDatos bd, Permisos permisos) {
        this.bd = bd;
        this.permisos = permisos;
    }

    /**
     * @param rango "hoy", "7d" (últimos 7 días) o "mes" (este mes)
     * @param areaId área a filtrar, o null para todas
     */
    public ReporteVista generar(Sesion s, String rango, Integer areaId) throws SQLException {
        permisos.exigirReportes(s);
        String tipo = rango == null ? "7d" : rango.trim().toLowerCase();
        if (!tipo.equals("hoy") && !tipo.equals("7d") && !tipo.equals("mes")) {
            new Validacion().cumple("rango", false, "Selecciona un intervalo de la lista.").verificar();
        }

        LocalDate hoy = permisos.ahora().toLocalDate();
        LocalDate desde = switch (tipo) {
            case "hoy" -> hoy;
            case "mes" -> hoy.withDayOfMonth(1);
            default -> hoy.minusDays(6);
        };
        LocalDate permitido = permisos.inicioPermitido(s).toLocalDate();
        if (desde.isBefore(permitido)) {
            desde = permitido;
        }
        final LocalDate inicio = desde;

        return bd.consultar(c -> {
            String nombreArea = null;
            if (areaId != null) {
                Area area = catalogo.area(c, areaId);
                if (area == null) {
                    new Validacion().cumple("areaId", false, "Selecciona un área de la lista.").verificar();
                }
                nombreArea = area.nombre();
            }
            List<Visita> lista = visitas.buscar(c, new Filtro(
                inicio.atStartOfDay(), hoy.plusDays(1).atStartOfDay(), null, null, areaId, null, 100_000));

            int finalizadas = (int) lista.stream().filter(v -> !v.activa()).count();
            OptionalDouble media = lista.stream()
                .filter(v -> v.duracionMinutos() != null).mapToInt(Visita::duracionMinutos).average();

            return new ReporteVista(
                tipo,
                switch (tipo) {
                    case "hoy" -> "Visitas por hora";
                    case "mes" -> "Visitas por semana";
                    default -> "Visitas por día";
                },
                inicio,
                hoy,
                nombreArea,
                lista.size(),
                finalizadas,
                lista.size() - finalizadas,
                media.isPresent() ? (int) Math.round(media.getAsDouble()) : null,
                barras(tipo, inicio, hoy, lista),
                porArea(lista)
            );
        });
    }

    private static List<BarraVista> barras(String tipo, LocalDate desde, LocalDate hasta, List<Visita> lista) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        switch (tipo) {
            case "hoy" -> {
                for (int hora = 7; hora <= 18; hora++) {
                    conteo.put(hora + " h", 0);
                }
                for (Visita v : lista) {
                    int hora = Math.max(7, Math.min(18, v.ingreso().getHour()));
                    conteo.merge(hora + " h", 1, Integer::sum);
                }
            }
            case "mes" -> {
                int semanas = (hasta.getDayOfMonth() - 1) / 7 + 1;
                for (int i = 1; i <= semanas; i++) {
                    conteo.put("Sem " + i, 0);
                }
                for (Visita v : lista) {
                    conteo.merge("Sem " + ((v.ingreso().getDayOfMonth() - 1) / 7 + 1), 1, Integer::sum);
                }
            }
            default -> {
                for (LocalDate d = desde; !d.isAfter(hasta); d = d.plusDays(1)) {
                    conteo.put(etiquetaDia(d), 0);
                }
                for (Visita v : lista) {
                    conteo.merge(etiquetaDia(v.ingreso().toLocalDate()), 1, Integer::sum);
                }
            }
        }
        List<BarraVista> barras = new ArrayList<>();
        conteo.forEach((etiqueta, total) -> barras.add(new BarraVista(etiqueta, total)));
        return barras;
    }

    private static String etiquetaDia(LocalDate fecha) {
        DayOfWeek dia = fecha.getDayOfWeek();
        return DIAS[dia.getValue() - 1] + " " + fecha.getDayOfMonth();
    }

    private static List<AreaResumen> porArea(List<Visita> lista) {
        Map<String, Integer> conteo = new LinkedHashMap<>();
        for (Visita v : lista) {
            conteo.merge(v.areaNombre(), 1, Integer::sum);
        }
        int total = lista.size();
        List<AreaResumen> resumen = new ArrayList<>();
        conteo.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
            .forEach(e -> resumen.add(new AreaResumen(
                e.getKey(), e.getValue(), total == 0 ? 0 : (int) Math.round(e.getValue() * 100.0 / total))));
        return resumen;
    }
}
