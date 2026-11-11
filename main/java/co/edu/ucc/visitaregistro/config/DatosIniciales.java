package co.edu.ucc.visitaregistro.config;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.bd.Jdbc;
import co.edu.ucc.visitaregistro.modelo.Modelos.Usuario;
import co.edu.ucc.visitaregistro.modelo.Rol;
import co.edu.ucc.visitaregistro.repositorio.CatalogoRepositorio;
import co.edu.ucc.visitaregistro.repositorio.UsuarioRepositorio;
import co.edu.ucc.visitaregistro.repositorio.VisitaRepositorio;
import co.edu.ucc.visitaregistro.seguridad.Contrasenas;
import co.edu.ucc.visitaregistro.servicio.Vistas.CuentaDemo;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Datos con los que arranca una base de datos vacía: áreas, anfitriones y
 * cuentas. Con DATOS_DEMO=true (valor por defecto) también crea cuentas de
 * prueba y visitas de ejemplo para presentar el sistema.
 */
public final class DatosIniciales {

    public static final List<CuentaDemo> CUENTAS_DEMO = List.of(
        new CuentaDemo("Recepcionista", "laura.vargas@empresa.com", "Recepcion2026"),
        new CuentaDemo("Empleado anfitrión", "ana.torres@empresa.com", "Anfitrion2026"),
        new CuentaDemo("Administrador de recepción", "miguel.santos@empresa.com", "Admin2026")
    );

    private static final String[][] ANFITRIONES = {
        {"Carlos Ruiz", "carlos.ruiz@empresa.com", "Finanzas"},
        {"Ana Torres", "ana.torres@empresa.com", "Operaciones"},
        {"Luis García", "luis.garcia@empresa.com", "Talento humano"},
        {"María Rojas", "maria.rojas@empresa.com", "Dirección"},
    };

    private final BaseDatos bd;
    private final Configuracion config;
    private final CatalogoRepositorio catalogo = new CatalogoRepositorio();
    private final UsuarioRepositorio usuarios = new UsuarioRepositorio();
    private final VisitaRepositorio visitas = new VisitaRepositorio();

    public DatosIniciales(BaseDatos bd, Configuracion config) {
        this.bd = bd;
        this.config = config;
    }

    public void cargar() throws SQLException {
        bd.transaccion(c -> {
            Map<String, Integer> anfitriones = cargarCatalogo(c);
            cargarCuentas(c, anfitriones);
            if (config.datosDemo() && visitas.contar(c) == 0) {
                cargarVisitasDemo(c, anfitriones);
            }
            return null;
        });
    }

    /** Áreas y anfitriones de la oficina, solo si la base está vacía. Devuelve anfitrión → id. */
    private Map<String, Integer> cargarCatalogo(Connection c) throws SQLException {
        Map<String, Integer> ids = new LinkedHashMap<>();
        if (catalogo.contarAreas(c) == 0) {
            Map<String, Integer> areas = new LinkedHashMap<>();
            for (String area : List.of("Finanzas", "Operaciones", "Talento humano", "Dirección")) {
                areas.put(area, catalogo.insertarArea(c, area));
            }
            for (String[] a : ANFITRIONES) {
                ids.put(a[0], catalogo.insertarAnfitrion(c, a[0], a[1], areas.get(a[2])));
            }
            System.out.println("[datos] Se crearon 4 áreas y 4 anfitriones.");
        } else {
            catalogo.anfitrionesActivos(c).forEach(a -> ids.put(a.nombre(), a.id()));
        }
        return ids;
    }

    private void cargarCuentas(Connection c, Map<String, Integer> anfitriones) throws SQLException {
        LocalDateTime ahora = ahora();
        boolean vacia = usuarios.contar(c) == 0;

        if (vacia && config.datosDemo()) {
            crear(c, "Laura Vargas", CUENTAS_DEMO.get(0), Rol.RECEPCIONISTA, true, null, ahora);
            crear(c, "Ana Torres", CUENTAS_DEMO.get(1), Rol.ANFITRION, true, anfitriones.get("Ana Torres"), ahora);
            crear(c, "Miguel Santos", CUENTAS_DEMO.get(2), Rol.ADMINISTRADOR, true, null, ahora);
            usuarios.insertar(c, "Jorge Peña", "jorge.pena@empresa.com", Contrasenas.cifrar(Contrasenas.generar()),
                Rol.RECEPCIONISTA, false, null, ahora);
            System.out.println("[datos] Cuentas de demostración creadas (ver README).");
        }

        // Administrador definido por variables de entorno (para producción)
        if (!config.adminCorreo().isEmpty() && !config.adminClave().isEmpty()) {
            Usuario existente = usuarios.porCorreo(c, config.adminCorreo());
            if (existente == null) {
                usuarios.insertar(c, config.adminNombre(), config.adminCorreo(), Contrasenas.cifrar(config.adminClave()),
                    Rol.ADMINISTRADOR, true, null, ahora);
                System.out.println("[datos] Administrador creado: " + config.adminCorreo());
            } else if (existente.rol() != Rol.ADMINISTRADOR || !existente.activo()) {
                usuarios.actualizar(c, existente.id(), existente.nombre(), Rol.ADMINISTRADOR, true, null);
                System.out.println("[datos] " + config.adminCorreo() + " ahora es administrador.");
            }
        } else if (vacia && !config.datosDemo()) {
            String clave = Contrasenas.generar();
            usuarios.insertar(c, "Administrador", "admin@visitaregistro.local", Contrasenas.cifrar(clave),
                Rol.ADMINISTRADOR, true, null, ahora);
            System.out.println("[datos] No se definió ADMIN_CORREO. Administrador temporal: admin@visitaregistro.local / "
                + clave + "  (cámbiala al entrar)");
        }
    }

    private void crear(Connection c, String nombre, CuentaDemo cuenta, Rol rol, boolean activo, Integer anfitrionId,
                       LocalDateTime ahora) throws SQLException {
        usuarios.insertar(c, nombre, cuenta.correo(), Contrasenas.cifrar(cuenta.contrasena()), rol, activo, anfitrionId, ahora);
    }

    /** Visitas de ejemplo: las 5 del prototipo más un historial de los últimos 30 días para los reportes. */
    private void cargarVisitasDemo(Connection c, Map<String, Integer> anfitriones) throws SQLException {
        Integer recepcion = Jdbc.uno(c, "SELECT id FROM usuarios_recepcion WHERE rol = 'RECEPCIONISTA' AND activo = TRUE",
            rs -> rs.getInt(1));
        if (recepcion == null || anfitriones.size() < 4) {
            return;
        }
        Map<String, Integer> areaDe = new LinkedHashMap<>();
        for (var a : catalogo.anfitrionesActivos(c)) {
            areaDe.put(a.nombre(), a.areaId());
        }
        LocalDateTime ahora = ahora();

        // Las del prototipo, relativas a la hora actual
        activa(c, "Mariana López", "1023456789", "Nexa Consultores", "Carlos Ruiz", "Reunión de proyecto",
            ahora.minusMinutes(138), anfitriones, areaDe, recepcion);
        activa(c, "Diego Herrera", "80456123", "Logística Uno", "Ana Torres", "Entrega",
            ahora.minusMinutes(95), anfitriones, areaDe, recepcion);
        activa(c, "Sofía Mendoza", "1098765432", null, "Luis García", "Entrevista",
            ahora.minusMinutes(39), anfitriones, areaDe, recepcion);
        finalizada(c, "Andrés Silva", "79123456", "Grupo Norte", "María Rojas", "Reunión comercial",
            ahora.minusMinutes(250), 86, anfitriones, areaDe, recepcion);
        finalizada(c, "Paula Castro", "52987654", "LegalPro", "Carlos Ruiz", "Asesoría",
            ahora.toLocalDate().minusDays(1).atTime(14, 10), 52, anfitriones, areaDe, recepcion);

        // Historial de ejemplo (siempre el mismo gracias a la semilla fija)
        String[] nombres = {"Camila Ortiz", "Julián Restrepo", "Valentina Pérez", "Santiago Gómez", "Daniela Ríos",
            "Mateo Cárdenas", "Isabella Muñoz", "Sebastián Castaño", "Gabriela Duarte", "Nicolás Herrera",
            "Laura Benítez", "Felipe Arango", "Natalia Quintero", "Andrés Montoya", "Juliana Vélez"};
        String[] empresas = {"Nexa Consultores", "Logística Uno", "Grupo Norte", "LegalPro", "Datacorp", null};
        List<String> motivos = List.of("Reunión de proyecto", "Reunión comercial", "Entrega", "Entrevista", "Asesoría");
        String[] lista = anfitriones.keySet().toArray(new String[0]);
        Random r = new Random(2026);
        for (int dia = 1; dia <= 30; dia++) {
            int cantidad = 3 + r.nextInt(8);
            for (int i = 0; i < cantidad; i++) {
                LocalDateTime ingreso = ahora.toLocalDate().minusDays(dia).atTime(7 + r.nextInt(10), r.nextInt(60));
                finalizada(c, nombres[r.nextInt(nombres.length)], null, empresas[r.nextInt(empresas.length)],
                    lista[r.nextInt(lista.length)], motivos.get(r.nextInt(motivos.size())),
                    ingreso, 20 + r.nextInt(130), anfitriones, areaDe, recepcion);
            }
        }
        System.out.println("[datos] Visitas de demostración creadas.");
    }

    private void activa(Connection c, String nombre, String documento, String empresa, String anfitrion, String motivo,
                        LocalDateTime ingreso, Map<String, Integer> anfitriones, Map<String, Integer> areaDe,
                        int usuario) throws SQLException {
        int visitante = visitas.insertarVisitante(c, nombre, documento, empresa, ingreso);
        visitas.insertar(c, visitante, anfitriones.get(anfitrion), areaDe.get(anfitrion), motivo, ingreso, usuario);
    }

    private void finalizada(Connection c, String nombre, String documento, String empresa, String anfitrion,
                            String motivo, LocalDateTime ingreso, int minutos, Map<String, Integer> anfitriones,
                            Map<String, Integer> areaDe, int usuario) throws SQLException {
        int visitante = visitas.insertarVisitante(c, nombre, documento, empresa, ingreso);
        int id = visitas.insertar(c, visitante, anfitriones.get(anfitrion), areaDe.get(anfitrion), motivo, ingreso, usuario);
        visitas.registrarSalida(c, id, ingreso.plusMinutes(minutos), minutos, usuario);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(ZoneId.of(config.zona().getId())).truncatedTo(ChronoUnit.SECONDS);
    }
}
