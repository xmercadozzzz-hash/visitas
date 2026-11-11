package co.edu.ucc.visitaregistro.bd;

import co.edu.ucc.visitaregistro.config.Configuracion;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Conexión a la base de datos relacional con un grupo (pool) pequeño
 * de conexiones reutilizables. Los planes gratuitos de MySQL en la nube
 * limitan cuántas conexiones se pueden abrir, por eso el tamaño es bajo.
 */
public final class BaseDatos implements AutoCloseable {

    @FunctionalInterface
    public interface Trabajo<T> {
        T ejecutar(Connection conexion) throws SQLException;
    }

    private final Configuracion config;
    private final BlockingQueue<Connection> libres;
    private final AtomicInteger abiertas = new AtomicInteger();
    private final int maximo;
    private volatile boolean cerrada;

    public BaseDatos(Configuracion config) {
        this.config = config;
        this.maximo = Math.max(1, config.tamanoPool());
        this.libres = new ArrayBlockingQueue<>(maximo);
        cargarControladores();
    }

    public Dialecto dialecto() {
        return config.dialecto();
    }

    /** Ejecuta un trabajo con una conexión prestada (cada sentencia se confirma sola). */
    public <T> T consultar(Trabajo<T> trabajo) throws SQLException {
        Connection conexion = obtener();
        boolean danada = false;
        try {
            return trabajo.ejecutar(conexion);
        } catch (SQLException e) {
            danada = esErrorDeConexion(e);
            throw e;
        } finally {
            devolver(conexion, danada);
        }
    }

    /** Ejecuta un trabajo dentro de una transacción: o se guarda todo o no se guarda nada. */
    public <T> T transaccion(Trabajo<T> trabajo) throws SQLException {
        Connection conexion = obtener();
        boolean danada = false;
        try {
            conexion.setAutoCommit(false);
            T resultado = trabajo.ejecutar(conexion);
            conexion.commit();
            return resultado;
        } catch (SQLException | RuntimeException e) {
            try {
                conexion.rollback();
            } catch (SQLException ignorada) {
                danada = true;
            }
            if (e instanceof SQLException sql && esErrorDeConexion(sql)) {
                danada = true;
            }
            throw e;
        } finally {
            try {
                conexion.setAutoCommit(true);
            } catch (SQLException e) {
                danada = true;
            }
            devolver(conexion, danada);
        }
    }

    private Connection obtener() throws SQLException {
        if (cerrada) {
            throw new SQLException("La base de datos está cerrada", "08003");
        }
        Connection conexion = libres.poll();
        while (conexion != null) {
            if (estaViva(conexion)) {
                return conexion;
            }
            descartar(conexion);
            conexion = libres.poll();
        }
        if (abiertas.incrementAndGet() <= maximo) {
            try {
                return abrir();
            } catch (SQLException e) {
                abiertas.decrementAndGet();
                throw e;
            }
        }
        abiertas.decrementAndGet();
        try {
            conexion = libres.poll(15, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (conexion == null) {
            throw new SQLException("No hay conexiones disponibles en este momento", "08001");
        }
        if (estaViva(conexion)) {
            return conexion;
        }
        descartar(conexion);
        abiertas.incrementAndGet();
        try {
            return abrir();
        } catch (SQLException e) {
            abiertas.decrementAndGet();
            throw e;
        }
    }

    private void devolver(Connection conexion, boolean danada) {
        if (danada || cerrada || !libres.offer(conexion)) {
            descartar(conexion);
        }
    }

    private Connection abrir() throws SQLException {
        return DriverManager.getConnection(config.jdbcUrl(), config.usuarioBd(), config.claveBd());
    }

    private boolean estaViva(Connection conexion) {
        try {
            return !conexion.isClosed() && conexion.isValid(3);
        } catch (SQLException e) {
            return false;
        }
    }

    private void descartar(Connection conexion) {
        abiertas.decrementAndGet();
        try {
            conexion.close();
        } catch (SQLException ignorada) {
            // ya estaba cerrada
        }
    }

    private static boolean esErrorDeConexion(SQLException e) {
        String estado = e.getSQLState();
        return estado != null && estado.startsWith("08");
    }

    private static void cargarControladores() {
        for (String clase : new String[] {"com.mysql.cj.jdbc.Driver", "org.hsqldb.jdbc.JDBCDriver"}) {
            try {
                Class.forName(clase);
            } catch (ClassNotFoundException ignorada) {
                // el controlador que no se use puede no estar presente
            }
        }
    }

    @Override
    public void close() {
        cerrada = true;
        if (config.dialecto() == Dialecto.HSQLDB) {
            try (Connection c = abrir()) {
                c.createStatement().execute("SHUTDOWN");
            } catch (SQLException ignorada) {
                // la base ya estaba detenida
            }
        }
        Connection conexion;
        while ((conexion = libres.poll()) != null) {
            descartar(conexion);
        }
    }
}
