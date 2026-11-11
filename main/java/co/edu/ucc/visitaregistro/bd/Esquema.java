package co.edu.ucc.visitaregistro.bd;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Estructura de la base de datos relacional: las cinco entidades del documento
 * (Áreas, Anfitriones, Usuarios de recepción, Visitantes y Visitas),
 * relacionadas por llaves foráneas. Las tablas que falten se crean al arrancar.
 *
 * Las restricciones CHECK hacen que la base misma rechace datos incoherentes
 * (RNF-04): una visita ACTIVA no puede tener salida, y una FINALIZADA debe
 * tener salida posterior al ingreso y duración.
 */
public final class Esquema {

    /** Nombre de la tabla y su CREATE TABLE (en el orden en que deben crearse). */
    public record Tabla(String nombre, String crear, List<String> indices) {
    }

    public static final List<Tabla> TABLAS = List.of(
        new Tabla("areas", """
            CREATE TABLE areas (
              id {ID},
              nombre VARCHAR(80) NOT NULL,
              activa BOOLEAN DEFAULT TRUE NOT NULL,
              CONSTRAINT uq_areas_nombre UNIQUE (nombre)
            ){FIN_TABLA}""", List.of()),

        new Tabla("anfitriones", """
            CREATE TABLE anfitriones (
              id {ID},
              nombre VARCHAR(100) NOT NULL,
              correo VARCHAR(150),
              area_id INTEGER NOT NULL,
              activo BOOLEAN DEFAULT TRUE NOT NULL,
              CONSTRAINT fk_anfitriones_area FOREIGN KEY (area_id) REFERENCES areas (id)
            ){FIN_TABLA}""", List.of()),

        new Tabla("usuarios_recepcion", """
            CREATE TABLE usuarios_recepcion (
              id {ID},
              nombre VARCHAR(100) NOT NULL,
              correo VARCHAR(150) NOT NULL,
              contrasena_hash VARCHAR(255) NOT NULL,
              rol VARCHAR(20) NOT NULL,
              activo BOOLEAN DEFAULT TRUE NOT NULL,
              anfitrion_id INTEGER,
              creado_en {FECHA_HORA} NOT NULL,
              ultimo_acceso {FECHA_HORA},
              CONSTRAINT uq_usuarios_correo UNIQUE (correo),
              CONSTRAINT ck_usuarios_rol CHECK (rol IN ('RECEPCIONISTA', 'ANFITRION', 'ADMINISTRADOR')),
              CONSTRAINT fk_usuarios_anfitrion FOREIGN KEY (anfitrion_id) REFERENCES anfitriones (id)
            ){FIN_TABLA}""", List.of()),

        new Tabla("visitantes", """
            CREATE TABLE visitantes (
              id {ID},
              nombre VARCHAR(100) NOT NULL,
              documento VARCHAR(20),
              empresa VARCHAR(100),
              creado_en {FECHA_HORA} NOT NULL
            ){FIN_TABLA}""", List.of(
            "CREATE INDEX idx_visitantes_documento ON visitantes (documento)"
        )),

        new Tabla("visitas", """
            CREATE TABLE visitas (
              id {ID},
              visitante_id INTEGER NOT NULL,
              anfitrion_id INTEGER NOT NULL,
              area_id INTEGER NOT NULL,
              motivo VARCHAR(60) NOT NULL,
              ingreso {FECHA_HORA} NOT NULL,
              salida {FECHA_HORA},
              duracion_minutos INTEGER,
              estado VARCHAR(12) NOT NULL,
              registrado_por INTEGER NOT NULL,
              salida_registrada_por INTEGER,
              CONSTRAINT fk_visitas_visitante FOREIGN KEY (visitante_id) REFERENCES visitantes (id),
              CONSTRAINT fk_visitas_anfitrion FOREIGN KEY (anfitrion_id) REFERENCES anfitriones (id),
              CONSTRAINT fk_visitas_area FOREIGN KEY (area_id) REFERENCES areas (id),
              CONSTRAINT fk_visitas_registro FOREIGN KEY (registrado_por) REFERENCES usuarios_recepcion (id),
              CONSTRAINT fk_visitas_salida FOREIGN KEY (salida_registrada_por) REFERENCES usuarios_recepcion (id),
              CONSTRAINT ck_visitas_estado CHECK (
                (estado = 'ACTIVA' AND salida IS NULL AND duracion_minutos IS NULL)
                OR (estado = 'FINALIZADA' AND salida IS NOT NULL AND salida >= ingreso AND duracion_minutos >= 0)
              )
            ){FIN_TABLA}""", List.of(
            "CREATE INDEX idx_visitas_ingreso ON visitas (ingreso)",
            "CREATE INDEX idx_visitas_estado ON visitas (estado)"
        ))
    );

    private Esquema() {
    }

    /** Crea las tablas que falten. Devuelve los nombres de las tablas creadas. */
    public static List<String> crearSiFalta(BaseDatos bd) throws SQLException {
        return bd.consultar(c -> {
            Set<String> existentes = tablasExistentes(c);
            List<String> creadas = new ArrayList<>();
            try (Statement st = c.createStatement()) {
                for (Tabla tabla : TABLAS) {
                    if (existentes.contains(tabla.nombre())) {
                        continue;
                    }
                    st.execute(bd.dialecto().adaptar(tabla.crear()));
                    for (String indice : tabla.indices()) {
                        st.execute(indice);
                    }
                    creadas.add(tabla.nombre());
                }
            }
            return creadas;
        });
    }

    /** El script completo para un dialecto (se usa para generar base_de_datos/esquema_mysql.sql). */
    public static String script(Dialecto dialecto) {
        StringBuilder sb = new StringBuilder();
        for (Tabla tabla : TABLAS) {
            sb.append(dialecto.adaptar(tabla.crear())).append(";\n\n");
            for (String indice : tabla.indices()) {
                sb.append(indice).append(";\n");
            }
            if (!tabla.indices().isEmpty()) {
                sb.append('\n');
            }
        }
        return sb.toString();
    }

    private static Set<String> tablasExistentes(Connection c) throws SQLException {
        Set<String> nombres = new HashSet<>();
        DatabaseMetaData meta = c.getMetaData();
        String catalogo = c.getCatalog();
        try (ResultSet rs = meta.getTables(catalogo, null, "%", new String[] {"TABLE"})) {
            while (rs.next()) {
                nombres.add(rs.getString("TABLE_NAME").toLowerCase(Locale.ROOT));
            }
        }
        return nombres;
    }
}
