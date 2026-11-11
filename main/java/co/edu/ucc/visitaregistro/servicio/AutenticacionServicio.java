package co.edu.ucc.visitaregistro.servicio;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.config.Configuracion;
import co.edu.ucc.visitaregistro.config.DatosIniciales;
import co.edu.ucc.visitaregistro.modelo.Modelos.Usuario;
import co.edu.ucc.visitaregistro.repositorio.UsuarioRepositorio;
import co.edu.ucc.visitaregistro.seguridad.Contrasenas;
import co.edu.ucc.visitaregistro.seguridad.LimiteIntentos;
import co.edu.ucc.visitaregistro.seguridad.Sesiones;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;
import co.edu.ucc.visitaregistro.servicio.Errores.ErrorNegocio;
import co.edu.ucc.visitaregistro.servicio.Vistas.CuentaDemo;
import co.edu.ucc.visitaregistro.servicio.Vistas.PermisosVista;
import co.edu.ucc.visitaregistro.servicio.Vistas.SesionVista;

import java.sql.SQLException;
import java.util.List;

/** Inicio y cierre de sesión, datos del usuario conectado y cambio de contraseña. */
public class AutenticacionServicio {

    private static final String CREDENCIALES_INCORRECTAS = "El correo o la contraseña no son correctos.";

    private final BaseDatos bd;
    private final Configuracion config;
    private final Permisos permisos;
    private final Sesiones sesiones;
    private final LimiteIntentos limite = new LimiteIntentos();
    private final UsuarioRepositorio usuarios = new UsuarioRepositorio();

    public AutenticacionServicio(BaseDatos bd, Configuracion config, Permisos permisos, Sesiones sesiones) {
        this.bd = bd;
        this.config = config;
        this.permisos = permisos;
        this.sesiones = sesiones;
    }

    public Sesion iniciarSesion(String correo, String contrasena, String ip) throws SQLException {
        String correoLimpio = Validacion.limpiar(correo);
        new Validacion()
            .requerido("correo", correoLimpio)
            .requerido("contrasena", contrasena == null || contrasena.isEmpty() ? null : contrasena)
            .verificar();

        long minutos = limite.minutosBloqueado(correoLimpio, ip);
        if (minutos > 0) {
            throw new ErrorNegocio(429, "Demasiados intentos fallidos. Espera " + minutos
                + (minutos == 1 ? " minuto" : " minutos") + " e inténtalo de nuevo.");
        }

        Usuario usuario = bd.consultar(c -> usuarios.porCorreo(c, correoLimpio));
        if (usuario == null) {
            Contrasenas.simularVerificacion(contrasena);
            limite.fallo(correoLimpio, ip);
            throw new ErrorNegocio(401, CREDENCIALES_INCORRECTAS);
        }
        if (!Contrasenas.coincide(contrasena, usuario.contrasenaHash())) {
            limite.fallo(correoLimpio, ip);
            throw new ErrorNegocio(401, CREDENCIALES_INCORRECTAS);
        }
        if (!usuario.activo()) {
            throw new ErrorNegocio(403, "Tu cuenta está desactivada. Comunícate con el administrador de recepción.");
        }
        limite.exito(correoLimpio, ip);
        bd.consultar(c -> {
            usuarios.registrarAcceso(c, usuario.id(), permisos.ahora());
            return null;
        });
        return sesiones.crear(usuario);
    }

    public void cerrarSesion(String token) {
        sesiones.cerrar(token);
    }

    public SesionVista datosSesion(Sesion s) throws SQLException {
        Usuario usuario = bd.consultar(c -> usuarios.porId(c, s.usuarioId()));
        if (usuario == null || !usuario.activo()) {
            sesiones.cerrar(s.token());
            throw new ErrorNegocio(401, "Tu sesión terminó. Vuelve a iniciar sesión.");
        }
        return new SesionVista(
            UsuarioServicio.vista(usuario),
            new PermisosVista(
                permisos.puedeRegistrarVisitas(s),
                permisos.puedeVerReportes(s),
                permisos.puedeGestionarUsuarios(s),
                permisos.puedeVerDocumento(s)
            ),
            config.nombreOficina(),
            permisos.ahora(),
            permisos.diasDeHistorial(s),
            config.retencionDias(),
            config.datosDemo()
        );
    }

    public void cambiarContrasena(Sesion s, String actual, String nueva, String confirmacion) throws SQLException {
        Validacion v = new Validacion()
            .requerido("actual", actual == null || actual.isEmpty() ? null : actual)
            .contrasenaSegura("nueva", nueva);
        if (nueva != null && !nueva.isEmpty()) {
            v.cumple("confirmacion", nueva.equals(confirmacion), "Las contraseñas no coinciden.");
        }
        v.verificar();
        bd.transaccion(c -> {
            Usuario usuario = usuarios.porId(c, s.usuarioId());
            if (usuario == null || !Contrasenas.coincide(actual, usuario.contrasenaHash())) {
                new Validacion().cumple("actual", false, "La contraseña actual no es correcta.").verificar();
            }
            usuarios.cambiarContrasena(c, s.usuarioId(), Contrasenas.cifrar(nueva));
            return null;
        });
    }

    /** Cuentas de prueba que se muestran en el inicio de sesión (solo con DATOS_DEMO activo). */
    public List<CuentaDemo> cuentasDemo() {
        return config.datosDemo() ? DatosIniciales.CUENTAS_DEMO : List.of();
    }
}
