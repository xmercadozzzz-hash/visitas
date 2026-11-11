package co.edu.ucc.visitaregistro.servicio;

import co.edu.ucc.visitaregistro.bd.BaseDatos;
import co.edu.ucc.visitaregistro.repositorio.VisitaRepositorio;

import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Política de retención (RF-08, regla de negocio 7): las visitas finalizadas
 * más antiguas que RETENCION_DIAS se eliminan, junto con los visitantes que
 * ya no tienen visitas. Se ejecuta al arrancar y luego una vez al día.
 */
public class RetencionServicio {

    private final BaseDatos bd;
    private final Permisos permisos;
    private final VisitaRepositorio visitas = new VisitaRepositorio();

    public RetencionServicio(BaseDatos bd, Permisos permisos) {
        this.bd = bd;
        this.permisos = permisos;
    }

    public LocalDateTime fechaLimite() {
        return permisos.ahora().toLocalDate().minusDays(permisos.retencionDias()).atStartOfDay();
    }

    /** Devuelve cuántas visitas se eliminaron. */
    public int aplicar() throws SQLException {
        LocalDateTime limite = fechaLimite();
        return bd.transaccion(c -> visitas.borrarAnterioresA(c, limite));
    }
}
