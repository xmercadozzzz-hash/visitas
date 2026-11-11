package co.edu.ucc.visitaregistro.servicio;

import co.edu.ucc.visitaregistro.config.Configuracion;
import co.edu.ucc.visitaregistro.modelo.Rol;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/**
 * Reglas de acceso por rol (RF-08, RNF-02) y la política de retención.
 * Están todas juntas aquí para que sea fácil revisarlas.
 *
 *  - Recepcionista: registra ingresos y salidas, ve visitas activas,
 *    consulta el historial de los últimos HISTORIAL_DIAS_RECEPCION días y los reportes.
 *  - Empleado anfitrión: solo ve las visitas que lo tienen como anfitrión,
 *    sin el documento de identidad del visitante.
 *  - Administrador de recepción: todo lo anterior, sin límite de días
 *    (dentro de la retención), y gestiona usuarios y permisos.
 *  - Retención: las visitas finalizadas con más de RETENCION_DIAS se borran.
 */
public final class Permisos {

    private final Configuracion config;

    public Permisos(Configuracion config) {
        this.config = config;
    }

    public ZoneId zona() {
        return config.zona();
    }

    /** Hora actual de la oficina, sin segundos fraccionarios (así se guarda en la base). */
    public LocalDateTime ahora() {
        return LocalDateTime.now(config.zona()).truncatedTo(ChronoUnit.SECONDS);
    }

    public boolean puedeRegistrarVisitas(Sesion s) {
        return s.es(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR);
    }

    public boolean puedeVerReportes(Sesion s) {
        return s.es(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR);
    }

    public boolean puedeGestionarUsuarios(Sesion s) {
        return s.es(Rol.ADMINISTRADOR);
    }

    public boolean puedeVerDocumento(Sesion s) {
        return s.es(Rol.RECEPCIONISTA, Rol.ADMINISTRADOR);
    }

    /** Si no es null, el usuario solo puede ver las visitas de este anfitrión. */
    public Integer anfitrionObligatorio(Sesion s) {
        if (s.es(Rol.ANFITRION)) {
            // un anfitrión sin empleado vinculado no ve ninguna visita
            return s.anfitrionId() == null ? -1 : s.anfitrionId();
        }
        return null;
    }

    /** Cuántos días hacia atrás puede consultar el historial este usuario. */
    public int diasDeHistorial(Sesion s) {
        return s.es(Rol.RECEPCIONISTA)
            ? Math.min(config.historialDiasRecepcion(), config.retencionDias())
            : config.retencionDias();
    }

    /** Fecha más antigua que este usuario puede consultar (inicio de ese día). */
    public LocalDateTime inicioPermitido(Sesion s) {
        return ahora().toLocalDate().minusDays(diasDeHistorial(s) - 1L).atStartOfDay();
    }

    public int retencionDias() {
        return config.retencionDias();
    }

    public void exigirRegistrar(Sesion s) {
        if (!puedeRegistrarVisitas(s)) {
            throw Errores.prohibido("Tu rol no permite registrar ingresos ni salidas.");
        }
    }

    public void exigirReportes(Sesion s) {
        if (!puedeVerReportes(s)) {
            throw Errores.prohibido("Tu rol no tiene acceso a los reportes.");
        }
    }

    public void exigirAdministrador(Sesion s) {
        if (!puedeGestionarUsuarios(s)) {
            throw Errores.prohibido("Solo el administrador de recepción puede gestionar usuarios.");
        }
    }
}
