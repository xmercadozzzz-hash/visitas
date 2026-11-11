package co.edu.ucc.visitaregistro.config;

import co.edu.ucc.visitaregistro.bd.Dialecto;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.ZoneId;
import java.util.Map;

/**
 * Toda la configuración sale de variables de entorno, con valores por
 * defecto que permiten ejecutar el sistema en el computador sin configurar nada.
 *
 * Base de datos, en este orden de prioridad:
 *  1. DB_URL (URL JDBC completa) + DB_USER + DB_PASSWORD
 *  2. DATABASE_URL o MYSQL_ADDON_URI (mysql://usuario:clave@host:puerto/base)
 *  3. DB_HOST / DB_PORT / DB_NAME / DB_USER / DB_PASSWORD
 *     (o los nombres de Clever Cloud: MYSQL_ADDON_HOST, _PORT, _DB, _USER, _PASSWORD)
 *  4. Sin nada de lo anterior: base HSQLDB en la carpeta "datos" (modo local).
 */
public record Configuracion(
    int puerto,
    String jdbcUrl,
    String usuarioBd,
    String claveBd,
    int tamanoPool,
    Dialecto dialecto,
    ZoneId zona,
    boolean datosDemo,
    int retencionDias,
    int historialDiasRecepcion,
    int sesionMinutos,
    boolean cookieSegura,
    Path carpetaWeb,
    String adminCorreo,
    String adminClave,
    String adminNombre,
    String nombreOficina
) {

    private static final String PARAMETROS_MYSQL =
        "useUnicode=true&characterEncoding=UTF-8&connectionTimeZone=UTC"
            + "&sslMode=PREFERRED&allowPublicKeyRetrieval=true&tcpKeepAlive=true";

    public static Configuracion desdeEntorno() {
        return desde(System.getenv());
    }

    public static Configuracion desde(Map<String, String> env) {
        String url;
        String usuario;
        String clave;

        String uriMysql = primero(env, "DATABASE_URL", "MYSQL_ADDON_URI");

        if (!texto(env, "DB_URL").isEmpty()) {
            url = texto(env, "DB_URL");
            usuario = texto(env, "DB_USER");
            clave = env.getOrDefault("DB_PASSWORD", "");
        } else if (uriMysql.startsWith("mysql://")) {
            URI uri = URI.create(uriMysql);
            String[] credenciales = (uri.getRawUserInfo() == null ? ":" : uri.getRawUserInfo()).split(":", 2);
            usuario = decodificar(credenciales[0]);
            clave = credenciales.length > 1 ? decodificar(credenciales[1]) : "";
            int puertoBd = uri.getPort() > 0 ? uri.getPort() : 3306;
            String base = uri.getPath() == null ? "" : uri.getPath().replaceFirst("^/", "");
            url = "jdbc:mysql://" + uri.getHost() + ":" + puertoBd + "/" + base + "?" + PARAMETROS_MYSQL;
        } else if (!primero(env, "DB_HOST", "MYSQL_ADDON_HOST").isEmpty()) {
            String host = primero(env, "DB_HOST", "MYSQL_ADDON_HOST");
            String puertoBd = valorOr(primero(env, "DB_PORT", "MYSQL_ADDON_PORT"), "3306");
            String base = valorOr(primero(env, "DB_NAME", "MYSQL_ADDON_DB"), "visitaregistro");
            usuario = primero(env, "DB_USER", "MYSQL_ADDON_USER");
            clave = env.getOrDefault("DB_PASSWORD", env.getOrDefault("MYSQL_ADDON_PASSWORD", ""));
            url = "jdbc:mysql://" + host + ":" + puertoBd + "/" + base + "?" + PARAMETROS_MYSQL;
        } else {
            url = "jdbc:hsqldb:file:" + valorOr(texto(env, "CARPETA_DATOS"), "datos") + "/visitaregistro"
                + ";sql.syntax_mys=true;hsqldb.write_delay=false";
            usuario = "SA";
            clave = "";
        }

        return new Configuracion(
            entero(env, "PORT", 8080),
            url,
            usuario,
            clave,
            entero(env, "DB_POOL", 3),
            Dialecto.desdeUrl(url),
            ZoneId.of(valorOr(texto(env, "ZONA_HORARIA"), "America/Bogota")),
            booleano(env, "DATOS_DEMO", true),
            entero(env, "RETENCION_DIAS", 365),
            entero(env, "HISTORIAL_DIAS_RECEPCION", 30),
            entero(env, "SESION_MINUTOS", 30),
            booleano(env, "COOKIE_SEGURA", false),
            Path.of(valorOr(texto(env, "CARPETA_WEB"), "web")).toAbsolutePath().normalize(),
            texto(env, "ADMIN_CORREO").toLowerCase(),
            env.getOrDefault("ADMIN_CONTRASENA", ""),
            valorOr(texto(env, "ADMIN_NOMBRE"), "Administrador"),
            valorOr(texto(env, "NOMBRE_OFICINA"), "Oficina Central")
        );
    }

    /** La URL sin contraseña, para escribirla en los registros del servidor. */
    public String descripcionBd() {
        return jdbcUrl.replaceAll("\\?.*$", "") + (usuarioBd.isEmpty() ? "" : " (usuario " + usuarioBd + ")");
    }

    private static String texto(Map<String, String> env, String nombre) {
        String valor = env.get(nombre);
        return valor == null ? "" : valor.trim();
    }

    private static String primero(Map<String, String> env, String... nombres) {
        for (String nombre : nombres) {
            if (!texto(env, nombre).isEmpty()) {
                return texto(env, nombre);
            }
        }
        return "";
    }

    private static String valorOr(String valor, String porDefecto) {
        return valor == null || valor.isEmpty() ? porDefecto : valor;
    }

    private static int entero(Map<String, String> env, String nombre, int porDefecto) {
        try {
            return Integer.parseInt(texto(env, nombre));
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }

    private static boolean booleano(Map<String, String> env, String nombre, boolean porDefecto) {
        String valor = texto(env, nombre).toLowerCase();
        if (valor.isEmpty()) {
            return porDefecto;
        }
        return valor.equals("true") || valor.equals("si") || valor.equals("sí") || valor.equals("1");
    }

    private static String decodificar(String valor) {
        return URLDecoder.decode(valor, StandardCharsets.UTF_8);
    }
}
