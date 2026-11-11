package co.edu.ucc.visitaregistro;

import co.edu.ucc.visitaregistro.bd.Jdbc;
import co.edu.ucc.visitaregistro.config.Configuracion;
import co.edu.ucc.visitaregistro.servicio.Permisos;
import co.edu.ucc.visitaregistro.servicio.RetencionServicio;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.TimeZone;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Pruebas del sistema completo: arranca el servidor con una base HSQLDB en
 * memoria y usa la API igual que el navegador. Verifican el registro de
 * ingresos y salidas, las visitas activas, la búsqueda y los permisos por rol.
 */
public class PruebasSistemaTest {

    private static Sistema sistema;
    private static Configuracion config;
    private static String base;

    @BeforeClass
    public static void arrancar() throws Exception {
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));
        config = Configuracion.desde(Map.of(
            "PORT", "0",
            "DB_URL", "jdbc:hsqldb:mem:pruebas" + UUID.randomUUID() + ";sql.syntax_mys=true",
            "DB_USER", "SA",
            "CARPETA_WEB", "web",
            "DATOS_DEMO", "true"
        ));
        sistema = Sistema.iniciar(config);
        base = "http://127.0.0.1:" + sistema.puerto();
    }

    @AfterClass
    public static void detener() {
        sistema.close();
    }

    /* ---------------- Cliente HTTP con cookies, como un navegador ---------------- */

    static final class Cliente {
        private static final HttpClient HTTP = HttpClient.newHttpClient();
        private String cookie;
        int estado;
        String cuerpo;

        JsonElement pedir(String metodo, String ruta, String json, boolean conCsrf) throws Exception {
            HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + ruta));
            if (cookie != null) {
                b.header("Cookie", cookie);
            }
            if (conCsrf) {
                b.header("X-Requested-With", "VisitaRegistro");
            }
            if (json != null) {
                b.header("Content-Type", "application/json").method(metodo, HttpRequest.BodyPublishers.ofString(json));
            } else {
                b.method(metodo, HttpRequest.BodyPublishers.noBody());
            }
            HttpResponse<String> r = HTTP.send(b.build(), HttpResponse.BodyHandlers.ofString());
            // guarda la cookie de sesión como lo haría el navegador
            r.headers().firstValue("Set-Cookie").ifPresent(valor -> {
                String par = valor.split(";")[0];
                cookie = par.endsWith("=") ? null : par;
            });
            estado = r.statusCode();
            cuerpo = r.body();
            return cuerpo.isEmpty() || !r.headers().firstValue("Content-Type").orElse("").contains("json")
                ? null : JsonParser.parseString(cuerpo);
        }

        JsonElement get(String ruta) throws Exception {
            return pedir("GET", ruta, null, false);
        }

        JsonElement post(String ruta, String json) throws Exception {
            return pedir("POST", ruta, json, true);
        }

        JsonElement put(String ruta, String json) throws Exception {
            return pedir("PUT", ruta, json, true);
        }

        Cliente entrar(String correo, String clave) throws Exception {
            post("/api/sesion", "{\"correo\":\"" + correo + "\",\"contrasena\":\"" + clave + "\"}");
            assertEquals("inicio de sesión de " + correo + ": " + cuerpo, 200, estado);
            return this;
        }
    }

    static Cliente recepcionista() throws Exception {
        return new Cliente().entrar("laura.vargas@empresa.com", "Recepcion2026");
    }

    static Cliente anfitriona() throws Exception {
        return new Cliente().entrar("ana.torres@empresa.com", "Anfitrion2026");
    }

    static Cliente administrador() throws Exception {
        return new Cliente().entrar("miguel.santos@empresa.com", "Admin2026");
    }

    static String documentoUnico() {
        return "D" + (System.nanoTime() % 1_000_000_000L);
    }

    /** Busca un anfitrión por nombre en los catálogos y devuelve su objeto JSON. */
    static JsonObject anfitrion(Cliente c, String nombre) throws Exception {
        for (JsonElement e : c.get("/api/catalogos").getAsJsonObject().getAsJsonArray("anfitriones")) {
            if (e.getAsJsonObject().get("nombre").getAsString().equals(nombre)) {
                return e.getAsJsonObject();
            }
        }
        throw new AssertionError("No existe el anfitrión " + nombre);
    }

    static JsonObject registrar(Cliente c, String nombre, String documento, JsonObject anfitrion) throws Exception {
        JsonElement r = c.post("/api/visitas", "{\"nombre\":\"" + nombre + "\",\"documento\":" + (documento == null ? "null" : "\"" + documento + "\"")
            + ",\"empresa\":\"Empresa de prueba\",\"motivo\":\"Entrega\",\"anfitrionId\":" + anfitrion.get("id")
            + ",\"areaId\":" + anfitrion.get("areaId") + "}");
        assertEquals(c.cuerpo, 201, c.estado);
        return r.getAsJsonObject();
    }

    static boolean contieneId(JsonArray visitas, int id) {
        for (JsonElement v : visitas) {
            if (v.getAsJsonObject().get("id").getAsInt() == id) {
                return true;
            }
        }
        return false;
    }

    /* ---------------- RF-01 a RF-04, RF-06: ingreso, activas y salida ---------------- */

    @Test
    public void registraIngresoActivaYSalidaConDuracion() throws Exception {
        Cliente c = recepcionista();
        JsonObject visita = registrar(c, "Andrea Martínez", documentoUnico(), anfitrion(c, "Carlos Ruiz"));
        int id = visita.get("id").getAsInt();
        assertEquals("ACTIVA", visita.get("estado").getAsString());
        assertTrue("guarda la hora de ingreso (RF-03)", visita.get("ingreso").getAsString().matches("\\d{4}-\\d{2}-\\d{2}T.*"));
        assertEquals("Carlos Ruiz", visita.get("anfitrion").getAsString());
        assertEquals("Finanzas", visita.get("area").getAsString());

        JsonArray activas = c.get("/api/visitas/activas").getAsJsonObject().getAsJsonArray("visitas");
        assertTrue("aparece en visitas activas (RF-06)", contieneId(activas, id));

        JsonObject salida = c.post("/api/visitas/" + id + "/salida", "{}").getAsJsonObject();
        assertEquals(200, c.estado);
        assertEquals("FINALIZADA", salida.get("estado").getAsString());
        assertFalse(salida.get("salida").isJsonNull());
        assertTrue("calcula la duración (RF-04)", salida.get("duracionMinutos").getAsInt() >= 0);

        activas = c.get("/api/visitas/activas").getAsJsonObject().getAsJsonArray("visitas");
        assertFalse("ya no está activa", contieneId(activas, id));

        c.post("/api/visitas/" + id + "/salida", "{}");
        assertEquals("no se puede registrar la salida dos veces", 409, c.estado);
    }

    @Test
    public void elServidorValidaElFormularioAunqueElNavegadorNoLoHaga() throws Exception {
        Cliente c = recepcionista();
        JsonObject r = c.post("/api/visitas",
            "{\"nombre\":\"X\",\"documento\":\"12 3\",\"motivo\":\"Inventado\",\"anfitrionId\":99999,\"areaId\":null}")
            .getAsJsonObject();
        assertEquals(400, c.estado);
        JsonObject campos = r.getAsJsonObject("campos");
        assertTrue(campos.has("nombre"));
        assertTrue(campos.has("documento"));
        assertTrue(campos.has("motivo"));
        assertTrue(campos.has("anfitrionId"));
        assertTrue(campos.has("areaId"));
    }

    @Test
    public void noPermiteDosVisitasActivasConElMismoDocumento() throws Exception {
        Cliente c = recepcionista();
        String documento = documentoUnico();
        registrar(c, "Pedro Gómez", documento, anfitrion(c, "Luis García"));
        c.post("/api/visitas", "{\"nombre\":\"Pedro Gómez\",\"documento\":\"" + documento
            + "\",\"motivo\":\"Entrega\",\"anfitrionId\":3,\"areaId\":3}");
        assertEquals(409, c.estado);
    }

    /* ---------------- RF-05, RF-08: búsqueda y permisos ---------------- */

    @Test
    public void elAnfitrionSoloVeSusVisitasYSinDocumento() throws Exception {
        Cliente recepcion = recepcionista();
        JsonObject deAna = registrar(recepcion, "Visitante De Ana", documentoUnico(), anfitrion(recepcion, "Ana Torres"));
        JsonObject deCarlos = registrar(recepcion, "Visitante De Carlos", documentoUnico(), anfitrion(recepcion, "Carlos Ruiz"));

        Cliente ana = anfitriona();
        JsonArray activas = ana.get("/api/visitas/activas").getAsJsonObject().getAsJsonArray("visitas");
        assertTrue(contieneId(activas, deAna.get("id").getAsInt()));
        assertFalse(contieneId(activas, deCarlos.get("id").getAsInt()));
        for (JsonElement v : activas) {
            assertEquals("Ana Torres", v.getAsJsonObject().get("anfitrion").getAsString());
            assertTrue("el documento queda oculto", v.getAsJsonObject().get("documento").isJsonNull());
        }

        JsonArray historial = ana.get("/api/visitas?anfitrionId=" + deCarlos.get("anfitrionId"))
            .getAsJsonObject().getAsJsonArray("visitas");
        for (JsonElement v : historial) {
            assertEquals("aunque pida otro anfitrión, solo recibe las suyas", "Ana Torres",
                v.getAsJsonObject().get("anfitrion").getAsString());
        }

        ana.get("/api/visitas/" + deCarlos.get("id").getAsInt());
        assertEquals("no puede abrir visitas de otros", 404, ana.estado);
    }

    @Test
    public void cadaRolTieneSusRestricciones() throws Exception {
        Cliente ana = anfitriona();
        ana.post("/api/visitas", "{\"nombre\":\"Alguien Nuevo\",\"motivo\":\"Entrega\",\"anfitrionId\":2,\"areaId\":2}");
        assertEquals("el anfitrión no registra ingresos", 403, ana.estado);
        ana.get("/api/reportes");
        assertEquals("el anfitrión no ve reportes", 403, ana.estado);
        ana.get("/api/usuarios");
        assertEquals(403, ana.estado);

        Cliente laura = recepcionista();
        laura.get("/api/usuarios");
        assertEquals("la recepcionista no gestiona usuarios", 403, laura.estado);
        laura.get("/api/reportes?rango=7d");
        assertEquals(200, laura.estado);
    }

    @Test
    public void laRecepcionistaSoloConsultaLosUltimos30Dias() throws Exception {
        Cliente laura = recepcionista();
        JsonObject r = laura.get("/api/visitas?desde=2020-01-01").getAsJsonObject();
        assertTrue(r.get("desdeAjustado").getAsBoolean());
        assertEquals(30, r.get("diasPermitidos").getAsInt());

        Cliente admin = administrador();
        JsonObject a = admin.get("/api/visitas?desde=2020-01-01").getAsJsonObject();
        assertEquals(365, a.get("diasPermitidos").getAsInt());
    }

    @Test
    public void buscaPorVisitanteYPorAnfitrion() throws Exception {
        Cliente c = recepcionista();
        JsonObject ana = anfitrion(c, "Ana Torres");
        String nombre = "Zoe Busqueda";
        int id = registrar(c, nombre, null, ana).get("id").getAsInt();

        JsonArray porNombre = c.get("/api/visitas?texto=zoe%20busq").getAsJsonObject().getAsJsonArray("visitas");
        assertTrue(contieneId(porNombre, id));

        JsonArray porAnfitrion = c.get("/api/visitas?anfitrionId=" + ana.get("id") + "&estado=ACTIVA")
            .getAsJsonObject().getAsJsonArray("visitas");
        assertTrue(contieneId(porAnfitrion, id));

        JsonArray porOtroAnfitrion = c.get("/api/visitas?anfitrionId=" + anfitrion(c, "María Rojas").get("id"))
            .getAsJsonObject().getAsJsonArray("visitas");
        assertFalse(contieneId(porOtroAnfitrion, id));
    }

    /* ---------------- RF-07: reportes ---------------- */

    @Test
    public void generaReportesPorDiaYArea() throws Exception {
        Cliente admin = administrador();
        JsonObject r = admin.get("/api/reportes?rango=7d").getAsJsonObject();
        assertEquals(7, r.getAsJsonArray("barras").size());
        int sumaBarras = 0;
        for (JsonElement b : r.getAsJsonArray("barras")) {
            sumaBarras += b.getAsJsonObject().get("total").getAsInt();
        }
        assertEquals(r.get("total").getAsInt(), sumaBarras);
        assertEquals(r.get("total").getAsInt(), r.get("finalizadas").getAsInt() + r.get("activas").getAsInt());
        assertTrue(r.getAsJsonArray("porArea").size() > 0);

        JsonObject hoy = admin.get("/api/reportes?rango=hoy&areaId=1").getAsJsonObject();
        assertEquals("Finanzas", hoy.get("area").getAsString());
        assertEquals(12, hoy.getAsJsonArray("barras").size());
    }

    /* ---------------- CU-07: usuarios ---------------- */

    @Test
    public void elAdministradorGestionaUsuarios() throws Exception {
        Cliente admin = administrador();
        String correo = "nuevo" + System.nanoTime() + "@empresa.com";

        admin.post("/api/usuarios", "{\"nombre\":\"Nuevo Anfitrion\",\"correo\":\"" + correo
            + "\",\"rol\":\"ANFITRION\",\"contrasena\":\"Clave2026\"}");
        assertEquals("un anfitrión debe quedar vinculado a un empleado", 400, admin.estado);

        JsonObject creado = admin.post("/api/usuarios", "{\"nombre\":\"Nuevo Recepcionista\",\"correo\":\"" + correo
            + "\",\"rol\":\"RECEPCIONISTA\",\"contrasena\":\"Clave2026\"}").getAsJsonObject();
        assertEquals(201, admin.estado);

        admin.post("/api/usuarios", "{\"nombre\":\"Repetido\",\"correo\":\"" + correo.toUpperCase()
            + "\",\"rol\":\"RECEPCIONISTA\",\"contrasena\":\"Clave2026\"}");
        assertEquals("no se repiten correos", 400, admin.estado);

        Cliente nuevo = new Cliente().entrar(correo, "Clave2026");
        nuevo.get("/api/panel");
        assertEquals(200, nuevo.estado);

        int id = creado.get("id").getAsInt();
        admin.put("/api/usuarios/" + id, "{\"nombre\":\"Nuevo Recepcionista\",\"rol\":\"RECEPCIONISTA\",\"activo\":false}");
        assertEquals(200, admin.estado);
        nuevo.get("/api/panel");
        assertEquals("al desactivarlo se cierra su sesión", 401, nuevo.estado);
        new Cliente().post("/api/sesion", "{\"correo\":\"" + correo + "\",\"contrasena\":\"Clave2026\"}");

        int propioId = admin.get("/api/sesion").getAsJsonObject().getAsJsonObject("usuario").get("id").getAsInt();
        admin.put("/api/usuarios/" + propioId, "{\"nombre\":\"Miguel Santos\",\"rol\":\"ADMINISTRADOR\",\"activo\":false}");
        assertEquals("el administrador no se desactiva a sí mismo", 409, admin.estado);
    }

    @Test
    public void unUsuarioDesactivadoNoPuedeEntrar() throws Exception {
        Cliente c = new Cliente();
        c.post("/api/sesion", "{\"correo\":\"jorge.pena@empresa.com\",\"contrasena\":\"cualquiera1\"}");
        assertEquals(401, c.estado);
    }

    /* ---------------- Seguridad ---------------- */

    @Test
    public void sinSesionNoSeAccedeALosDatos() throws Exception {
        Cliente c = new Cliente();
        assertTrue(c.get("/api/sesion").isJsonNull());
        c.get("/api/panel");
        assertEquals(401, c.estado);
        c.get("/api/visitas");
        assertEquals(401, c.estado);
        c.post("/api/sesion", "{\"correo\":\"laura.vargas@empresa.com\",\"contrasena\":\"incorrecta\"}");
        assertEquals(401, c.estado);
    }

    @Test
    public void rechazaCambiosSinLaCabeceraAntiCsrf() throws Exception {
        Cliente c = recepcionista();
        c.pedir("POST", "/api/visitas", "{\"nombre\":\"Sin Cabecera\"}", false);
        assertEquals(403, c.estado);
    }

    @Test
    public void bloqueaDespuesDeCincoIntentosFallidos() throws Exception {
        Cliente c = new Cliente();
        String correo = "intruso" + System.nanoTime() + "@empresa.com";
        for (int i = 0; i < 5; i++) {
            c.post("/api/sesion", "{\"correo\":\"" + correo + "\",\"contrasena\":\"mala" + i + "\"}");
            assertEquals(401, c.estado);
        }
        c.post("/api/sesion", "{\"correo\":\"" + correo + "\",\"contrasena\":\"otra\"}");
        assertEquals(429, c.estado);
    }

    @Test
    public void cambiaLaContrasenaPropia() throws Exception {
        Cliente admin = administrador();
        String correo = "clave" + System.nanoTime() + "@empresa.com";
        admin.post("/api/usuarios", "{\"nombre\":\"Cambia Clave\",\"correo\":\"" + correo
            + "\",\"rol\":\"RECEPCIONISTA\",\"contrasena\":\"Inicial2026\"}");
        Cliente u = new Cliente().entrar(correo, "Inicial2026");
        u.post("/api/perfil/contrasena", "{\"actual\":\"mala\",\"nueva\":\"Nueva2026x\",\"confirmacion\":\"Nueva2026x\"}");
        assertEquals(400, u.estado);
        u.post("/api/perfil/contrasena", "{\"actual\":\"Inicial2026\",\"nueva\":\"Nueva2026x\",\"confirmacion\":\"Nueva2026x\"}");
        assertEquals(200, u.estado);
        new Cliente().entrar(correo, "Nueva2026x");
    }

    @Test
    public void entregaElFrontendYNoDejaSalirDeLaCarpetaWeb() throws Exception {
        Cliente c = new Cliente();
        c.get("/");
        assertEquals(200, c.estado);
        assertTrue(c.cuerpo.contains("<div id=\"root\"></div>"));
        c.get("/css/estilos.css");
        assertEquals(200, c.estado);
        c.get("/%2e%2e/%2e%2e/etc/passwd");
        assertEquals(404, c.estado);
    }

    /* ---------------- RNF-04 y política de retención ---------------- */

    @Test
    public void laBaseRechazaVisitasIncoherentes() throws Exception {
        try {
            sistema.baseDatos().consultar(c -> Jdbc.actualizar(c,
                "INSERT INTO visitas (visitante_id, anfitrion_id, area_id, motivo, ingreso, estado, registrado_por) "
                    + "VALUES (1, 1, 1, 'Entrega', ?, 'FINALIZADA', 1)", LocalDateTime.now()));
            fail("Una visita finalizada sin hora de salida no debía guardarse");
        } catch (SQLException esperado) {
            // la restricción CHECK de la tabla visitas la rechazó
        }
    }

    @Test
    public void laRetencionBorraLasVisitasViejas() throws Exception {
        LocalDateTime hace400 = LocalDateTime.now(ZoneId.of("America/Bogota")).minusDays(400).truncatedTo(ChronoUnit.SECONDS);
        int id = sistema.baseDatos().transaccion(c -> {
            int visitante = Jdbc.insertar(c, "INSERT INTO visitantes (nombre, creado_en) VALUES ('Muy Antiguo', ?)", hace400);
            return Jdbc.insertar(c, "INSERT INTO visitas (visitante_id, anfitrion_id, area_id, motivo, ingreso, salida, "
                + "duracion_minutos, estado, registrado_por) VALUES (?, 1, 1, 'Entrega', ?, ?, 30, 'FINALIZADA', 1)",
                visitante, hace400, hace400.plusMinutes(30));
        });
        int borradas = new RetencionServicio(sistema.baseDatos(), new Permisos(config)).aplicar();
        assertTrue(borradas >= 1);
        int quedan = sistema.baseDatos().consultar(c -> Jdbc.contar(c, "SELECT COUNT(*) FROM visitas WHERE id = ?", id));
        assertEquals(0, quedan);
    }
}
