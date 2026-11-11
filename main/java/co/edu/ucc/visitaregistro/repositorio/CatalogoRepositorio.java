package co.edu.ucc.visitaregistro.repositorio;

import co.edu.ucc.visitaregistro.bd.Jdbc;
import co.edu.ucc.visitaregistro.modelo.Modelos.Anfitrion;
import co.edu.ucc.visitaregistro.modelo.Modelos.Area;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

/** Áreas de la oficina y empleados anfitriones que reciben visitantes. */
public class CatalogoRepositorio {

    static final String SQL_AREAS_ACTIVAS =
        "SELECT id, nombre, activa FROM areas WHERE activa = TRUE ORDER BY nombre";
    static final String SQL_AREA_POR_ID =
        "SELECT id, nombre, activa FROM areas WHERE id = ?";
    static final String SQL_INSERTAR_AREA =
        "INSERT INTO areas (nombre, activa) VALUES (?, TRUE)";
    static final String SQL_CONTAR_AREAS =
        "SELECT COUNT(*) FROM areas";

    static final String SQL_ANFITRIONES_ACTIVOS =
        "SELECT a.id, a.nombre, a.correo, a.area_id, ar.nombre AS area_nombre, a.activo "
            + "FROM anfitriones a JOIN areas ar ON ar.id = a.area_id "
            + "WHERE a.activo = TRUE ORDER BY a.nombre";
    static final String SQL_ANFITRION_POR_ID =
        "SELECT a.id, a.nombre, a.correo, a.area_id, ar.nombre AS area_nombre, a.activo "
            + "FROM anfitriones a JOIN areas ar ON ar.id = a.area_id WHERE a.id = ?";
    static final String SQL_INSERTAR_ANFITRION =
        "INSERT INTO anfitriones (nombre, correo, area_id, activo) VALUES (?, ?, ?, TRUE)";

    public List<Area> areasActivas(Connection c) throws SQLException {
        return Jdbc.lista(c, SQL_AREAS_ACTIVAS, CatalogoRepositorio::leerArea);
    }

    public Area area(Connection c, int id) throws SQLException {
        return Jdbc.uno(c, SQL_AREA_POR_ID, CatalogoRepositorio::leerArea, id);
    }

    public int insertarArea(Connection c, String nombre) throws SQLException {
        return Jdbc.insertar(c, SQL_INSERTAR_AREA, nombre);
    }

    public int contarAreas(Connection c) throws SQLException {
        return Jdbc.contar(c, SQL_CONTAR_AREAS);
    }

    public List<Anfitrion> anfitrionesActivos(Connection c) throws SQLException {
        return Jdbc.lista(c, SQL_ANFITRIONES_ACTIVOS, CatalogoRepositorio::leerAnfitrion);
    }

    public Anfitrion anfitrion(Connection c, int id) throws SQLException {
        return Jdbc.uno(c, SQL_ANFITRION_POR_ID, CatalogoRepositorio::leerAnfitrion, id);
    }

    public int insertarAnfitrion(Connection c, String nombre, String correo, int areaId) throws SQLException {
        return Jdbc.insertar(c, SQL_INSERTAR_ANFITRION, nombre, correo, areaId);
    }

    private static Area leerArea(ResultSet rs) throws SQLException {
        return new Area(rs.getInt("id"), rs.getString("nombre"), rs.getBoolean("activa"));
    }

    private static Anfitrion leerAnfitrion(ResultSet rs) throws SQLException {
        return new Anfitrion(
            rs.getInt("id"),
            rs.getString("nombre"),
            rs.getString("correo"),
            rs.getInt("area_id"),
            rs.getString("area_nombre"),
            rs.getBoolean("activo")
        );
    }
}
