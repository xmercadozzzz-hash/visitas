package co.edu.ucc.visitaregistro.modelo;

import java.time.LocalDateTime;

/**
 * Entidades de la base de datos (las cinco del documento: Áreas, Anfitriones,
 * Visitantes, Visitas y Usuarios de recepción). Son registros inmutables:
 * los repositorios los leen de la base y los servicios los usan.
 */
public final class Modelos {

    private Modelos() {
    }

    public record Area(int id, String nombre, boolean activa) {
    }

    public record Anfitrion(int id, String nombre, String correo, int areaId, String areaNombre, boolean activo) {
    }

    public record Visitante(int id, String nombre, String documento, String empresa) {
    }

    public enum EstadoVisita { ACTIVA, FINALIZADA }

    /** Una visita con los nombres de su visitante, anfitrión y área ya unidos. */
    public record Visita(
        int id,
        int visitanteId,
        String visitanteNombre,
        String documento,
        String empresa,
        int anfitrionId,
        String anfitrionNombre,
        int areaId,
        String areaNombre,
        String motivo,
        LocalDateTime ingreso,
        LocalDateTime salida,
        Integer duracionMinutos,
        EstadoVisita estado,
        String registradoPor
    ) {
        public boolean activa() {
            return estado == EstadoVisita.ACTIVA;
        }
    }

    public record Usuario(
        int id,
        String nombre,
        String correo,
        String contrasenaHash,
        Rol rol,
        boolean activo,
        Integer anfitrionId,
        String anfitrionNombre,
        LocalDateTime creadoEn,
        LocalDateTime ultimoAcceso
    ) {
    }
}
