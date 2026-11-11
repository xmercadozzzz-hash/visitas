package co.edu.ucc.visitaregistro.repositorio;

import co.edu.ucc.visitaregistro.bd.Jdbc;
import co.edu.ucc.visitaregistro.modelo.Modelos.Usuario;
import co.edu.ucc.visitaregistro.modelo.Rol;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/** Cuentas de acceso al sistema (tabla usuarios_recepcion). */
public class UsuarioRepositorio {

    private static final String COLUMNAS =
        "SELECT u.id, u.nombre, u.correo, u.contrasena_hash, u.rol, u.activo, u.anfitrion_id, "
            + "a.nombre AS anfitrion_nombre, u.creado_en, u.ultimo_acceso "
            + "FROM usuarios_recepcion u LEFT JOIN anfitriones a ON a.id = u.anfitrion_id ";

    static final String SQL_POR_CORREO = COLUMNAS + "WHERE u.correo = ?";
    static final String SQL_POR_ID = COLUMNAS + "WHERE u.id = ?";
    static final String SQL_LISTAR = COLUMNAS + "ORDER BY u.activo DESC, u.nombre";
    static final String SQL_INSERTAR =
        "INSERT INTO usuarios_recepcion (nombre, correo, contrasena_hash, rol, activo, anfitrion_id, creado_en) "
            + "VALUES (?, ?, ?, ?, ?, ?, ?)";
    static final String SQL_ACTUALIZAR =
        "UPDATE usuarios_recepcion SET nombre = ?, rol = ?, activo = ?, anfitrion_id = ? WHERE id = ?";
    static final String SQL_CAMBIAR_CONTRASENA =
        "UPDATE usuarios_recepcion SET contrasena_hash = ? WHERE id = ?";
    static final String SQL_REGISTRAR_ACCESO =
        "UPDATE usuarios_recepcion SET ultimo_acceso = ? WHERE id = ?";
    static final String SQL_CONTAR =
        "SELECT COUNT(*) FROM usuarios_recepcion";
    static final String SQL_CONTAR_ADMINS_ACTIVOS =
        "SELECT COUNT(*) FROM usuarios_recepcion WHERE rol = 'ADMINISTRADOR' AND activo = TRUE";

    public Usuario porCorreo(Connection c, String correo) throws SQLException {
        return Jdbc.uno(c, SQL_POR_CORREO, UsuarioRepositorio::leer, correo.trim().toLowerCase());
    }

    public Usuario porId(Connection c, int id) throws SQLException {
        return Jdbc.uno(c, SQL_POR_ID, UsuarioRepositorio::leer, id);
    }

    public List<Usuario> listar(Connection c) throws SQLException {
        return Jdbc.lista(c, SQL_LISTAR, UsuarioRepositorio::leer);
    }

    public int insertar(Connection c, String nombre, String correo, String hash, Rol rol, boolean activo,
                        Integer anfitrionId, LocalDateTime creadoEn) throws SQLException {
        return Jdbc.insertar(c, SQL_INSERTAR, nombre, correo.trim().toLowerCase(), hash, rol, activo, anfitrionId, creadoEn);
    }

    public void actualizar(Connection c, int id, String nombre, Rol rol, boolean activo, Integer anfitrionId)
        throws SQLException {
        Jdbc.actualizar(c, SQL_ACTUALIZAR, nombre, rol, activo, anfitrionId, id);
    }

    public void cambiarContrasena(Connection c, int id, String hash) throws SQLException {
        Jdbc.actualizar(c, SQL_CAMBIAR_CONTRASENA, hash, id);
    }

    public void registrarAcceso(Connection c, int id, LocalDateTime momento) throws SQLException {
        Jdbc.actualizar(c, SQL_REGISTRAR_ACCESO, momento, id);
    }

    public int contar(Connection c) throws SQLException {
        return Jdbc.contar(c, SQL_CONTAR);
    }

    public int contarAdministradoresActivos(Connection c) throws SQLException {
        return Jdbc.contar(c, SQL_CONTAR_ADMINS_ACTIVOS);
    }

    private static Usuario leer(ResultSet rs) throws SQLException {
        return new Usuario(
            rs.getInt("id"),
            rs.getString("nombre"),
            rs.getString("correo"),
            rs.getString("contrasena_hash"),
            Rol.valueOf(rs.getString("rol")),
            rs.getBoolean("activo"),
            Jdbc.enteroONulo(rs, "anfitrion_id"),
            rs.getString("anfitrion_nombre"),
            Jdbc.fecha(rs, "creado_en"),
            Jdbc.fecha(rs, "ultimo_acceso")
        );
    }
}
