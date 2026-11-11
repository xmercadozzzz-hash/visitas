package co.edu.ucc.visitaregistro.web.controladores;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.config.Configuracion;
import co.edu.ucc.visitaregistro.seguridad.Sesiones;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;
import co.edu.ucc.visitaregistro.servicio.AutenticacionServicio;
import co.edu.ucc.visitaregistro.servicio.Vistas.Mensaje;
import co.edu.ucc.visitaregistro.web.Enrutador;
import co.edu.ucc.visitaregistro.web.Enrutador.Acceso;
import co.edu.ucc.visitaregistro.web.Intercambio;

import java.util.Map;

/** Rutas de inicio y cierre de sesión, perfil y estado del servidor. */
public final class AutenticacionControlador {

    record Credenciales(String correo, String contrasena) {
    }

    record CambioContrasena(String actual, String nueva, String confirmacion) {
    }

    private final AutenticacionServicio servicio;
    private final Sesiones sesiones;
    private final Configuracion config;
    private final BaseDatos bd;

    public AutenticacionControlador(AutenticacionServicio servicio, Sesiones sesiones, Configuracion config, BaseDatos bd) {
        this.servicio = servicio;
        this.sesiones = sesiones;
        this.config = config;
        this.bd = bd;
    }

    public void registrar(Enrutador r) {
        r.post("/api/sesion", Acceso.PUBLICO, (x, s) -> {
            Credenciales c = x.cuerpo(Credenciales.class);
            Sesion nueva = servicio.iniciarSesion(c.correo(), c.contrasena(), x.ip());
            x.ponerCookie(cookie(x, nueva.token(), sesiones.maxAgeSegundos()));
            return servicio.datosSesion(nueva);
        });

        r.post("/api/sesion/cerrar", Acceso.PUBLICO, (x, s) -> {
            servicio.cerrarSesion(x.cookie(Sesiones.COOKIE));
            x.ponerCookie(cookie(x, "", 0));
            return new Mensaje("Sesión cerrada.");
        });

        // Sin sesión responde null (no 401) para que el navegador no lo marque como error al abrir la página
        r.get("/api/sesion", Acceso.PUBLICO, (x, s) -> s == null ? null : servicio.datosSesion(s));

        r.get("/api/demo", Acceso.PUBLICO, (x, s) -> servicio.cuentasDemo());

        r.post("/api/perfil/contrasena", Acceso.CON_SESION, (x, s) -> {
            CambioContrasena c = x.cuerpo(CambioContrasena.class);
            servicio.cambiarContrasena(s, c.actual(), c.nueva(), c.confirmacion());
            return new Mensaje("Contraseña actualizada.");
        });

        r.get("/api/salud", Acceso.PUBLICO, (x, s) -> {
            boolean baseOk;
            try {
                baseOk = bd.consultar(c -> c.isValid(3));
            } catch (Exception e) {
                baseOk = false;
            }
            return new Enrutador.Respuesta(baseOk ? 200 : 503,
                Map.of("estado", baseOk ? "ok" : "sin base de datos", "motor", config.dialecto().name()));
        });
    }

    /** Cookie de sesión: HttpOnly (JavaScript no la puede leer) y SameSite=Lax. */
    private String cookie(Intercambio x, String valor, long maxAge) {
        String secure = config.cookieSegura() || x.esHttps() ? "; Secure" : "";
        return Sesiones.COOKIE + "=" + valor + "; Path=/; HttpOnly; SameSite=Lax; Max-Age=" + maxAge + secure;
    }
}
