package co.edu.ucc.visitaregistro.servicio;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.modelo.Modelos.Anfitrion;
import co.edu.ucc.visitaregistro.modelo.Modelos.Usuario;
import co.edu.ucc.visitaregistro.modelo.Rol;
import co.edu.ucc.visitaregistro.repositorio.CatalogoRepositorio;
import co.edu.ucc.visitaregistro.repositorio.UsuarioRepositorio;
import co.edu.ucc.visitaregistro.seguridad.Contrasenas;
import co.edu.ucc.visitaregistro.seguridad.Sesiones;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;
import co.edu.ucc.visitaregistro.servicio.Vistas.UsuarioVista;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/** CU-07: el administrador gestiona los usuarios y sus roles. */
public class UsuarioServicio {

    public record SolicitudUsuario(
        String nombre, String correo, String rol, String contrasena, Integer anfitrionId, Boolean activo) {
    }

    private final BaseDatos bd;
    private final Permisos permisos;
    private final Sesiones sesiones;
    private final UsuarioRepositorio usuarios = new UsuarioRepositorio();
    private final CatalogoRepositorio catalogo = new CatalogoRepositorio();

    public UsuarioServicio(BaseDatos bd, Permisos permisos, Sesiones sesiones) {
        this.bd = bd;
        this.permisos = permisos;
        this.sesiones = sesiones;
    }

    public List<UsuarioVista> listar(Sesion s) throws SQLException {
        permisos.exigirAdministrador(s);
        return bd.consultar(c -> usuarios.listar(c).stream().map(UsuarioServicio::vista).toList());
    }

    public UsuarioVista crear(Sesion s, SolicitudUsuario solicitud) throws SQLException {
        permisos.exigirAdministrador(s);
        String nombre = Validacion.limpiar(solicitud.nombre());
        String correo = Validacion.limpiar(solicitud.correo());
        correo = correo == null ? null : correo.toLowerCase();
        Rol rol = Rol.desdeTexto(solicitud.rol());

        Validacion v = new Validacion()
            .nombrePersona("nombre", nombre)
            .correo("correo", correo)
            .cumple("rol", rol != null, "Selecciona un rol de la lista.")
            .contrasenaSegura("contrasena", solicitud.contrasena());
        final String correoFinal = correo;

        return bd.transaccion(c -> {
            if (correoFinal != null && !v.tieneError("correo")) {
                v.cumple("correo", usuarios.porCorreo(c, correoFinal) == null, "Ya existe un usuario con este correo.");
            }
            Integer anfitrionId = validarAnfitrion(c, v, rol, solicitud.anfitrionId());
            v.verificar();
            boolean activo = solicitud.activo() == null || solicitud.activo();
            int id = usuarios.insertar(c, nombre, correoFinal, Contrasenas.cifrar(solicitud.contrasena()), rol, activo,
                anfitrionId, permisos.ahora());
            return vista(usuarios.porId(c, id));
        });
    }

    public UsuarioVista actualizar(Sesion s, int id, SolicitudUsuario solicitud) throws SQLException {
        permisos.exigirAdministrador(s);
        String nombre = Validacion.limpiar(solicitud.nombre());
        Rol rol = Rol.desdeTexto(solicitud.rol());
        boolean cambiaContrasena = solicitud.contrasena() != null && !solicitud.contrasena().isEmpty();

        Validacion v = new Validacion()
            .nombrePersona("nombre", nombre)
            .cumple("rol", rol != null, "Selecciona un rol de la lista.");
        if (cambiaContrasena) {
            v.contrasenaSegura("contrasena", solicitud.contrasena());
        }

        UsuarioVista resultado = bd.transaccion(c -> {
            Usuario actual = usuarios.porId(c, id);
            if (actual == null) {
                throw Errores.noEncontrado("El usuario no existe.");
            }
            boolean activo = solicitud.activo() == null ? actual.activo() : solicitud.activo();
            if (id == s.usuarioId() && (rol != Rol.ADMINISTRADOR || !activo)) {
                throw Errores.conflicto("No puedes quitarte el rol de administrador ni desactivar tu propia cuenta.");
            }
            boolean dejaDeSerAdmin = actual.rol() == Rol.ADMINISTRADOR && actual.activo()
                && (rol != Rol.ADMINISTRADOR || !activo);
            if (dejaDeSerAdmin && usuarios.contarAdministradoresActivos(c) <= 1) {
                throw Errores.conflicto("Debe quedar al menos un administrador activo.");
            }
            Integer anfitrionId = validarAnfitrion(c, v, rol, solicitud.anfitrionId());
            v.verificar();

            usuarios.actualizar(c, id, nombre, rol, activo, anfitrionId);
            if (cambiaContrasena) {
                usuarios.cambiarContrasena(c, id, Contrasenas.cifrar(solicitud.contrasena()));
            }
            return vista(usuarios.porId(c, id));
        });
        // con otro rol o desactivado, debe volver a iniciar sesión
        if (id != s.usuarioId()) {
            sesiones.cerrarDeUsuario(id);
        }
        return resultado;
    }

    /** El rol "Empleado anfitrión" debe quedar vinculado a un anfitrión para saber qué visitas puede ver. */
    private Integer validarAnfitrion(Connection c, Validacion v, Rol rol, Integer anfitrionId) throws SQLException {
        if (rol != Rol.ANFITRION) {
            return null;
        }
        if (anfitrionId == null) {
            v.cumple("anfitrionId", false, "Selecciona el empleado anfitrión que corresponde a esta cuenta.");
            return null;
        }
        Anfitrion anfitrion = catalogo.anfitrion(c, anfitrionId);
        v.cumple("anfitrionId", anfitrion != null && anfitrion.activo(), "Selecciona un anfitrión de la lista.");
        return anfitrionId;
    }

    static UsuarioVista vista(Usuario u) {
        return new UsuarioVista(u.id(), u.nombre(), u.correo(), u.rol().name(), u.rol().etiqueta(), u.activo(),
            u.anfitrionId(), u.anfitrionNombre(), u.ultimoAcceso());
    }
}
