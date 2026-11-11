package co.edu.ucc.visitaregistro.web;

import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Relaciona cada ruta de la API (método + dirección) con el código que la atiende. */
public final class Enrutador {

    /** Si la ruta se puede usar sin iniciar sesión. */
    public enum Acceso { PUBLICO, CON_SESION }

    @FunctionalInterface
    public interface Manejador {
        /** Devuelve el objeto que se enviará como JSON, o una {@link Respuesta} con otro código HTTP. */
        Object atender(Intercambio x, Sesion sesion) throws Exception;
    }

    /** Para responder con un código distinto de 200 (por ejemplo 201 Creado). */
    public record Respuesta(int estado, Object cuerpo) {
    }

    record Ruta(String metodo, Pattern patron, Acceso acceso, Manejador manejador) {
    }

    record Coincidencia(Ruta ruta, boolean metodoDistinto) {
    }

    private final List<Ruta> rutas = new ArrayList<>();

    public void get(String ruta, Acceso acceso, Manejador manejador) {
        agregar("GET", ruta, acceso, manejador);
    }

    public void post(String ruta, Acceso acceso, Manejador manejador) {
        agregar("POST", ruta, acceso, manejador);
    }

    public void put(String ruta, Acceso acceso, Manejador manejador) {
        agregar("PUT", ruta, acceso, manejador);
    }

    private void agregar(String metodo, String ruta, Acceso acceso, Manejador manejador) {
        // "/api/visitas/{id}" se convierte en la expresión ^/api/visitas/(?<id>\d+)$
        String expresion = "^" + ruta.replace("{id}", "(?<id>\\d{1,9})") + "$";
        rutas.add(new Ruta(metodo, Pattern.compile(expresion), acceso, manejador));
    }

    Coincidencia buscar(Intercambio x) {
        boolean otraRutaConEsaDireccion = false;
        String metodo = x.metodo().equals("HEAD") ? "GET" : x.metodo();
        for (Ruta ruta : rutas) {
            Matcher m = ruta.patron().matcher(x.ruta());
            if (!m.matches()) {
                continue;
            }
            if (!ruta.metodo().equals(metodo)) {
                otraRutaConEsaDireccion = true;
                continue;
            }
            if (ruta.patron().pattern().contains("(?<id>")) {
                x.parametroRuta("id", m.group("id"));
            }
            return new Coincidencia(ruta, false);
        }
        return otraRutaConEsaDireccion ? new Coincidencia(null, true) : null;
    }
}
