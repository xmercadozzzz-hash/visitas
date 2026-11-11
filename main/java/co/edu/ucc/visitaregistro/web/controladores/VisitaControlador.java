package co.edu.ucc.visitaregistro.web.controladores;

import co.edu.ucc.visitaregistro.servicio.ReporteServicio;
import co.edu.ucc.visitaregistro.servicio.VisitaServicio;
import co.edu.ucc.visitaregistro.servicio.VisitaServicio.FiltroHistorial;
import co.edu.ucc.visitaregistro.servicio.VisitaServicio.SolicitudVisita;
import co.edu.ucc.visitaregistro.web.Enrutador;
import co.edu.ucc.visitaregistro.web.Enrutador.Acceso;
import co.edu.ucc.visitaregistro.web.Enrutador.Respuesta;

/** Rutas de visitas, catálogos, panel principal y reportes. */
public final class VisitaControlador {

    private final VisitaServicio visitas;
    private final ReporteServicio reportes;

    public VisitaControlador(VisitaServicio visitas, ReporteServicio reportes) {
        this.visitas = visitas;
        this.reportes = reportes;
    }

    public void registrar(Enrutador r) {
        r.get("/api/catalogos", Acceso.CON_SESION, (x, s) -> visitas.catalogos());

        r.get("/api/panel", Acceso.CON_SESION, (x, s) -> visitas.panel(s));

        r.get("/api/visitas/activas", Acceso.CON_SESION, (x, s) -> visitas.activas(s, x.parametro("texto")));

        r.get("/api/visitas", Acceso.CON_SESION, (x, s) -> visitas.historial(s, new FiltroHistorial(
            x.parametro("desde"),
            x.parametro("hasta"),
            x.parametro("texto"),
            x.parametroEntero("anfitrionId"),
            x.parametroEntero("areaId"),
            x.parametro("estado")
        )));

        r.post("/api/visitas", Acceso.CON_SESION,
            (x, s) -> new Respuesta(201, visitas.registrar(s, x.cuerpo(SolicitudVisita.class))));

        r.get("/api/visitas/{id}", Acceso.CON_SESION, (x, s) -> visitas.detalle(s, x.id()));

        r.post("/api/visitas/{id}/salida", Acceso.CON_SESION, (x, s) -> visitas.registrarSalida(s, x.id()));

        r.get("/api/reportes", Acceso.CON_SESION,
            (x, s) -> reportes.generar(s, x.parametro("rango"), x.parametroEntero("areaId")));
    }
}
