package co.edu.ucc.visitaregistro;

import co.edu.ucc.visitaregistro.config.Configuracion;

import java.util.TimeZone;

/**
 * Punto de entrada: VisitaRegistro, sistema de registro de visitantes y control de recepción.
 *
 * Ejecutar en el computador:   ejecutar.cmd (Windows) o ./ejecutar.sh (Linux/macOS)
 * Configuración:               variables de entorno (ver README.md)
 */
public final class Aplicacion {

    private Aplicacion() {
    }

    public static void main(String[] args) throws Exception {
        // Las fechas se guardan con la hora de la oficina (ZONA_HORARIA); la JVM en UTC
        // evita conversiones automáticas entre Java y la base de datos.
        TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

        Sistema sistema = Sistema.iniciar(Configuracion.desdeEntorno());
        Runtime.getRuntime().addShutdownHook(new Thread(sistema::close, "cierre"));
    }
}
