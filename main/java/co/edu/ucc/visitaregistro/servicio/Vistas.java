package co.edu.ucc.visitaregistro.servicio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Lo que la API envía al navegador (en JSON). Se separa de las entidades
 * para enviar solo lo necesario y ocultar lo que el rol no puede ver.
 */
public final class Vistas {

    private Vistas() {
    }

    public record VisitaVista(
        int id,
        String visitante,
        String documento,
        boolean documentoOculto,
        String empresa,
        int anfitrionId,
        String anfitrion,
        int areaId,
        String area,
        String motivo,
        LocalDateTime ingreso,
        LocalDateTime salida,
        Integer duracionMinutos,
        Integer minutosTranscurridos,
        String estado,
        String registradoPor
    ) {
    }

    public record UsuarioVista(
        int id,
        String nombre,
        String correo,
        String rol,
        String rolEtiqueta,
        boolean activo,
        Integer anfitrionId,
        String anfitrion,
        LocalDateTime ultimoAcceso
    ) {
    }

    public record PermisosVista(boolean registrar, boolean reportes, boolean usuarios, boolean verDocumento) {
    }

    public record SesionVista(
        UsuarioVista usuario,
        PermisosVista permisos,
        String oficina,
        LocalDateTime ahora,
        int diasHistorial,
        int retencionDias,
        boolean datosDemo
    ) {
    }

    public record Opcion(int id, String nombre) {
    }

    public record AnfitrionOpcion(int id, String nombre, int areaId, String area) {
    }

    public record CatalogosVista(List<Opcion> areas, List<AnfitrionOpcion> anfitriones, List<String> motivos) {
    }

    public record PanelVista(
        LocalDateTime ahora,
        int activas,
        int ingresosUltimaHora,
        int entradasHoy,
        int entradasAyer,
        int finalizadasHoy,
        Integer duracionPromedioHoy,
        Integer duracionPromedioAyer,
        int prolongadas,
        List<VisitaVista> ultimas
    ) {
    }

    public record ActivasVista(LocalDateTime actualizado, List<VisitaVista> visitas) {
    }

    public record HistorialVista(
        List<VisitaVista> visitas,
        boolean truncado,
        int limite,
        LocalDate desdeAplicado,
        LocalDate hastaAplicado,
        int diasPermitidos,
        boolean desdeAjustado
    ) {
    }

    public record BarraVista(String etiqueta, int total) {
    }

    public record AreaResumen(String area, int total, int porcentaje) {
    }

    public record ReporteVista(
        String rango,
        String titulo,
        LocalDate desde,
        LocalDate hasta,
        String area,
        int total,
        int finalizadas,
        int activas,
        Integer duracionPromedio,
        List<BarraVista> barras,
        List<AreaResumen> porArea
    ) {
    }

    public record CuentaDemo(String rol, String correo, String contrasena) {
    }

    public record Mensaje(String mensaje) {
    }

    public record ErrorVista(String error, Map<String, String> campos) {
    }
}
