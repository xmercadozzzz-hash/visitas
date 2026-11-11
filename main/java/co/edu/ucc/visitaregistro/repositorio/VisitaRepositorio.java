package co.edu.ucc.visitaregistro.repositorio;

import co.edu.ucc.visitaregistro.bd.Jdbc;
import co.edu.ucc.visitaregistro.modelo.Modelos.EstadoVisita;
import co.edu.ucc.visitaregistro.modelo.Modelos.Visita;
import co.edu.ucc.visitaregistro.modelo.Modelos.Visitante;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Visitantes y visitas: ingresos, salidas, búsquedas y datos para reportes. */
public class VisitaRepositorio {

    /** Criterios de búsqueda. Los que vienen en null no se aplican. */
    public record Filtro(
        LocalDateTime desde,
        LocalDateTime hasta,
        String texto,
        Integer anfitrionId,
        Integer areaId,
        EstadoVisita estado,
        int limite
    ) {
    }

    /** Texto SQL más sus parámetros, en el mismo orden de los ?. */
    public record Consulta(String sql, Object[] parametros) {
    }

    static final String SQL_BASE =
        "SELECT v.id, v.motivo, v.ingreso, v.salida, v.duracion_minutos, v.estado, "
            + "vi.id AS visitante_id, vi.nombre AS visitante_nombre, vi.documento, vi.empresa, "
            + "a.id AS anfitrion_id, a.nombre AS anfitrion_nombre, "
            + "ar.id AS area_id, ar.nombre AS area_nombre, u.nombre AS registrado_por "
            + "FROM visitas v "
            + "JOIN visitantes vi ON vi.id = v.visitante_id "
            + "JOIN anfitriones a ON a.id = v.anfitrion_id "
            + "JOIN areas ar ON ar.id = v.area_id "
            + "JOIN usuarios_recepcion u ON u.id = v.registrado_por ";

    static final String SQL_POR_ID = SQL_BASE + "WHERE v.id = ?";
    static final String SQL_INSERTAR =
        "INSERT INTO visitas (visitante_id, anfitrion_id, area_id, motivo, ingreso, estado, registrado_por) "
            + "VALUES (?, ?, ?, ?, ?, 'ACTIVA', ?)";
    static final String SQL_REGISTRAR_SALIDA =
        "UPDATE visitas SET salida = ?, duracion_minutos = ?, estado = 'FINALIZADA', salida_registrada_por = ? "
            + "WHERE id = ? AND estado = 'ACTIVA'";
    static final String SQL_ACTIVA_POR_DOCUMENTO =
        "SELECT COUNT(*) FROM visitas v JOIN visitantes vi ON vi.id = v.visitante_id "
            + "WHERE vi.documento = ? AND v.estado = 'ACTIVA'";
    static final String SQL_BORRAR_ANTERIORES =
        "DELETE FROM visitas WHERE estado = 'FINALIZADA' AND ingreso < ?";
    static final String SQL_CONTAR = "SELECT COUNT(*) FROM visitas";

    static final String SQL_VISITANTE_POR_DOCUMENTO =
        "SELECT id, nombre, documento, empresa FROM visitantes WHERE documento = ?";
    static final String SQL_INSERTAR_VISITANTE =
        "INSERT INTO visitantes (nombre, documento, empresa, creado_en) VALUES (?, ?, ?, ?)";
    static final String SQL_ACTUALIZAR_VISITANTE =
        "UPDATE visitantes SET nombre = ?, empresa = ? WHERE id = ?";
    static final String SQL_BORRAR_VISITANTES_SIN_VISITAS =
        "DELETE FROM visitantes WHERE id NOT IN (SELECT visitante_id FROM visitas)";

    /** Arma la consulta de búsqueda con solo los filtros que se usaron. */
    public static Consulta construirBusqueda(Filtro f) {
        StringBuilder sql = new StringBuilder(SQL_BASE).append("WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();
        if (f.desde() != null) {
            sql.append(" AND v.ingreso >= ?");
            parametros.add(f.desde());
        }
        if (f.hasta() != null) {
            sql.append(" AND v.ingreso < ?");
            parametros.add(f.hasta());
        }
        if (f.texto() != null && !f.texto().isBlank()) {
            // "!" escapa los comodines % y _ que escriba el usuario (funciona igual en MySQL y HSQLDB)
            String patron = "%" + f.texto().trim().toLowerCase()
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
            sql.append(" AND (LOWER(vi.nombre) LIKE ? ESCAPE '!' OR LOWER(vi.documento) LIKE ? ESCAPE '!'"
                + " OR LOWER(vi.empresa) LIKE ? ESCAPE '!' OR LOWER(a.nombre) LIKE ? ESCAPE '!')");
            parametros.add(patron);
            parametros.add(patron);
            parametros.add(patron);
            parametros.add(patron);
        }
        if (f.anfitrionId() != null) {
            sql.append(" AND v.anfitrion_id = ?");
            parametros.add(f.anfitrionId());
        }
        if (f.areaId() != null) {
            sql.append(" AND v.area_id = ?");
            parametros.add(f.areaId());
        }
        if (f.estado() != null) {
            sql.append(" AND v.estado = ?");
            parametros.add(f.estado());
        }
        sql.append(" ORDER BY v.ingreso DESC, v.id DESC LIMIT ?");
        parametros.add(f.limite());
        return new Consulta(sql.toString(), parametros.toArray());
    }

    public List<Visita> buscar(Connection c, Filtro filtro) throws SQLException {
        Consulta consulta = construirBusqueda(filtro);
        return Jdbc.lista(c, consulta.sql(), VisitaRepositorio::leer, consulta.parametros());
    }

    public Visita porId(Connection c, int id) throws SQLException {
        return Jdbc.uno(c, SQL_POR_ID, VisitaRepositorio::leer, id);
    }

    public int insertar(Connection c, int visitanteId, int anfitrionId, int areaId, String motivo,
                        LocalDateTime ingreso, int registradoPor) throws SQLException {
        return Jdbc.insertar(c, SQL_INSERTAR, visitanteId, anfitrionId, areaId, motivo, ingreso, registradoPor);
    }

    /** Devuelve false si la visita ya no estaba activa (por ejemplo, alguien registró la salida antes). */
    public boolean registrarSalida(Connection c, int id, LocalDateTime salida, int minutos, int usuarioId)
        throws SQLException {
        return Jdbc.actualizar(c, SQL_REGISTRAR_SALIDA, salida, minutos, usuarioId, id) == 1;
    }

    public boolean tieneVisitaActiva(Connection c, String documento) throws SQLException {
        return Jdbc.contar(c, SQL_ACTIVA_POR_DOCUMENTO, documento) > 0;
    }

    public int contar(Connection c) throws SQLException {
        return Jdbc.contar(c, SQL_CONTAR);
    }

    /** Política de retención: borra las visitas finalizadas anteriores a la fecha límite. */
    public int borrarAnterioresA(Connection c, LocalDateTime limite) throws SQLException {
        int borradas = Jdbc.actualizar(c, SQL_BORRAR_ANTERIORES, limite);
        Jdbc.actualizar(c, SQL_BORRAR_VISITANTES_SIN_VISITAS);
        return borradas;
    }

    public Visitante visitantePorDocumento(Connection c, String documento) throws SQLException {
        return Jdbc.uno(c, SQL_VISITANTE_POR_DOCUMENTO,
            rs -> new Visitante(rs.getInt("id"), rs.getString("nombre"), rs.getString("documento"), rs.getString("empresa")),
            documento);
    }

    public int insertarVisitante(Connection c, String nombre, String documento, String empresa, LocalDateTime creado)
        throws SQLException {
        return Jdbc.insertar(c, SQL_INSERTAR_VISITANTE, nombre, documento, empresa, creado);
    }

    public void actualizarVisitante(Connection c, int id, String nombre, String empresa) throws SQLException {
        Jdbc.actualizar(c, SQL_ACTUALIZAR_VISITANTE, nombre, empresa, id);
    }

    private static Visita leer(ResultSet rs) throws SQLException {
        return new Visita(
            rs.getInt("id"),
            rs.getInt("visitante_id"),
            rs.getString("visitante_nombre"),
            rs.getString("documento"),
            rs.getString("empresa"),
            rs.getInt("anfitrion_id"),
            rs.getString("anfitrion_nombre"),
            rs.getInt("area_id"),
            rs.getString("area_nombre"),
            rs.getString("motivo"),
            Jdbc.fecha(rs, "ingreso"),
            Jdbc.fecha(rs, "salida"),
            Jdbc.enteroONulo(rs, "duracion_minutos"),
            EstadoVisita.valueOf(rs.getString("estado")),
            rs.getString("registrado_por")
        );
    }
}
