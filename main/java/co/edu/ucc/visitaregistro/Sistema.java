package co.edu.ucc.visitaregistro;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.bd.Esquema;
import co.edu.ucc.visitaregistro.config.Configuracion;
import co.edu.ucc.visitaregistro.config.DatosIniciales;
import co.edu.ucc.visitaregistro.seguridad.Sesiones;
import co.edu.ucc.visitaregistro.servicio.AutenticacionServicio;
import co.edu.ucc.visitaregistro.servicio.Permisos;
import co.edu.ucc.visitaregistro.servicio.ReporteServicio;
import co.edu.ucc.visitaregistro.servicio.RetencionServicio;
import co.edu.ucc.visitaregistro.servicio.UsuarioServicio;
import co.edu.ucc.visitaregistro.servicio.VisitaServicio;
import co.edu.ucc.visitaregistro.web.Enrutador;
import co.edu.ucc.visitaregistro.web.Servidor;
import co.edu.ucc.visitaregistro.web.controladores.AutenticacionControlador;
import co.edu.ucc.visitaregistro.web.controladores.UsuarioControlador;
import co.edu.ucc.visitaregistro.web.controladores.VisitaControlador;

import java.sql.SQLException;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/** Arma todas las piezas del sistema (base de datos, servicios, API y servidor) y las arranca. */
public final class Sistema implements AutoCloseable {

    private final BaseDatos bd;
    private final Servidor servidor;
    private final RetencionServicio retencion;
    private final ScheduledExecutorService tareas;

    private Sistema(BaseDatos bd, Servidor servidor, RetencionServicio retencion, ScheduledExecutorService tareas) {
        this.bd = bd;
        this.servidor = servidor;
        this.retencion = retencion;
        this.tareas = tareas;
    }

    public static Sistema iniciar(Configuracion config) throws Exception {
        System.out.println("[inicio] Base de datos: " + config.descripcionBd());
        BaseDatos bd = new BaseDatos(config);
        prepararBase(bd, config);

        Sesiones sesiones = new Sesiones(config.sesionMinutos());
        Permisos permisos = new Permisos(config);
        VisitaServicio visitas = new VisitaServicio(bd, permisos);
        ReporteServicio reportes = new ReporteServicio(bd, permisos);
        UsuarioServicio usuarios = new UsuarioServicio(bd, permisos, sesiones);
        AutenticacionServicio autenticacion = new AutenticacionServicio(bd, config, permisos, sesiones);
        RetencionServicio retencion = new RetencionServicio(bd, permisos);

        Enrutador enrutador = new Enrutador();
        new AutenticacionControlador(autenticacion, sesiones, config, bd).registrar(enrutador);
        new VisitaControlador(visitas, reportes).registrar(enrutador);
        new UsuarioControlador(usuarios).registrar(enrutador);

        Servidor servidor = new Servidor(config.puerto(), config.carpetaWeb(), enrutador, sesiones);
        servidor.iniciar();

        ScheduledExecutorService tareas = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread hilo = new Thread(r, "tareas-programadas");
            hilo.setDaemon(true);
            return hilo;
        });
        Sistema sistema = new Sistema(bd, servidor, retencion, tareas);
        tareas.scheduleAtFixedRate(sistema::aplicarRetencion, 0, 24, TimeUnit.HOURS);
        tareas.scheduleAtFixedRate(sesiones::limpiarVencidas, 10, 10, TimeUnit.MINUTES);

        System.out.println("[inicio] VisitaRegistro listo en http://localhost:" + servidor.puerto());
        return sistema;
    }

    /** Crea las tablas y los datos iniciales. Reintenta unos segundos si la base aún no responde. */
    private static void prepararBase(BaseDatos bd, Configuracion config) throws Exception {
        SQLException ultimo = null;
        for (int intento = 1; intento <= 5; intento++) {
            try {
                List<String> creadas = Esquema.crearSiFalta(bd);
                if (!creadas.isEmpty()) {
                    System.out.println("[inicio] Tablas creadas: " + String.join(", ", creadas));
                }
                new DatosIniciales(bd, config).cargar();
                return;
            } catch (SQLException e) {
                ultimo = e;
                System.err.println("[inicio] La base de datos no responde (intento " + intento + " de 5): " + e.getMessage());
                Thread.sleep(3000L * intento);
            }
        }
        throw new IllegalStateException("No se pudo conectar con la base de datos. Revisa las variables DB_HOST, "
            + "DB_PORT, DB_NAME, DB_USER y DB_PASSWORD.", ultimo);
    }

    private void aplicarRetencion() {
        try {
            int borradas = retencion.aplicar();
            if (borradas > 0) {
                System.out.println("[retención] Se eliminaron " + borradas + " visitas anteriores a " + retencion.fechaLimite().toLocalDate());
            }
        } catch (Exception e) {
            System.err.println("[retención] No se pudo aplicar: " + e.getMessage());
        }
    }

    public int puerto() {
        return servidor.puerto();
    }

    public BaseDatos baseDatos() {
        return bd;
    }

    @Override
    public void close() {
        tareas.shutdownNow();
        servidor.detener();
        bd.close();
    }
}
