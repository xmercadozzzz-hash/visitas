package co.edu.ucc.visitaregistro.servicio;

import java.util.LinkedHashMap;
import java.util.Map;

/** Errores que el servidor devuelve al navegador con un mensaje claro. */
public final class Errores {

    private Errores() {
    }

    /** Error de una regla de negocio o de permisos, con su código HTTP. */
    public static class ErrorNegocio extends RuntimeException {
        private final int estadoHttp;

        public ErrorNegocio(int estadoHttp, String mensaje) {
            super(mensaje);
            this.estadoHttp = estadoHttp;
        }

        public int estadoHttp() {
            return estadoHttp;
        }
    }

    /** Uno o varios campos del formulario no son válidos (RNF-03: validación en el servidor). */
    public static class ErrorValidacion extends RuntimeException {
        private final Map<String, String> campos;

        public ErrorValidacion(Map<String, String> campos) {
            super("Revisa los campos marcados.");
            this.campos = new LinkedHashMap<>(campos);
        }

        public Map<String, String> campos() {
            return campos;
        }
    }

    public static ErrorNegocio noEncontrado(String mensaje) {
        return new ErrorNegocio(404, mensaje);
    }

    public static ErrorNegocio prohibido(String mensaje) {
        return new ErrorNegocio(403, mensaje);
    }

    public static ErrorNegocio conflicto(String mensaje) {
        return new ErrorNegocio(409, mensaje);
    }
}
