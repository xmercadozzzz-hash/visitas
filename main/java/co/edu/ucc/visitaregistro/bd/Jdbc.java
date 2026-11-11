package co.edu.ucc.visitaregistro.bd;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Ayudas para usar JDBC con sentencias preparadas. Todas las consultas del
 * sistema pasan los valores como parámetros (?), nunca pegados al texto SQL,
 * para evitar inyección SQL.
 */
public final class Jdbc {

    @FunctionalInterface
    public interface Lector<T> {
        T leer(ResultSet fila) throws SQLException;
    }

    private Jdbc() {
    }

    public static <T> List<T> lista(Connection c, String sql, Lector<T> lector, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            asignar(ps, parametros);
            try (ResultSet rs = ps.executeQuery()) {
                List<T> resultado = new ArrayList<>();
                while (rs.next()) {
                    resultado.add(lector.leer(rs));
                }
                return resultado;
            }
        }
    }

    public static <T> T uno(Connection c, String sql, Lector<T> lector, Object... parametros) throws SQLException {
        List<T> filas = lista(c, sql, lector, parametros);
        return filas.isEmpty() ? null : filas.get(0);
    }

    public static int contar(Connection c, String sql, Object... parametros) throws SQLException {
        Integer valor = uno(c, sql, fila -> fila.getInt(1), parametros);
        return valor == null ? 0 : valor;
    }

    public static int actualizar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            asignar(ps, parametros);
            return ps.executeUpdate();
        }
    }

    /** INSERT que devuelve el id generado por la base de datos. */
    public static int insertar(Connection c, String sql, Object... parametros) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            asignar(ps, parametros);
            ps.executeUpdate();
            try (ResultSet claves = ps.getGeneratedKeys()) {
                if (!claves.next()) {
                    throw new SQLException("La base de datos no devolvió el id generado");
                }
                return claves.getInt(1);
            }
        }
    }

    public static LocalDateTime fecha(ResultSet rs, String columna) throws SQLException {
        Timestamp valor = rs.getTimestamp(columna);
        return valor == null ? null : valor.toLocalDateTime();
    }

    public static Integer enteroONulo(ResultSet rs, String columna) throws SQLException {
        int valor = rs.getInt(columna);
        return rs.wasNull() ? null : valor;
    }

    private static void asignar(PreparedStatement ps, Object... parametros) throws SQLException {
        for (int i = 0; i < parametros.length; i++) {
            Object valor = parametros[i];
            int posicion = i + 1;
            if (valor == null) {
                ps.setNull(posicion, Types.NULL);
            } else if (valor instanceof LocalDateTime fecha) {
                ps.setTimestamp(posicion, Timestamp.valueOf(fecha));
            } else if (valor instanceof Integer entero) {
                ps.setInt(posicion, entero);
            } else if (valor instanceof Boolean logico) {
                ps.setBoolean(posicion, logico);
            } else if (valor instanceof Enum<?> enumerado) {
                ps.setString(posicion, enumerado.name());
            } else {
                ps.setString(posicion, valor.toString());
            }
        }
    }
}
