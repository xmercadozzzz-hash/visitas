package co.edu.ucc.visitaregistro.servicio;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Valida los datos que llegan del navegador. El navegador ya valida, pero
 * el servidor vuelve a validar porque cualquiera puede saltarse el formulario.
 */
public final class Validacion {

    public static final String OBLIGATORIO = "Este campo es obligatorio.";
    private static final Pattern NOMBRE = Pattern.compile("^[\\p{L}][\\p{L} .'-]*$");
    private static final Pattern DOCUMENTO = Pattern.compile("^[A-Za-z0-9-]+$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$");
    private static final Pattern CONTROL = Pattern.compile("\\p{Cntrl}");

    private final Map<String, String> errores = new LinkedHashMap<>();

    /** Quita espacios sobrantes; devuelve null si queda vacío. */
    public static String limpiar(String valor) {
        if (valor == null) {
            return null;
        }
        String limpio = CONTROL.matcher(valor).replaceAll(" ").trim().replaceAll("\\s{2,}", " ");
        return limpio.isEmpty() ? null : limpio;
    }

    public Validacion requerido(String campo, Object valor) {
        if (valor == null || (valor instanceof String s && s.isBlank())) {
            agregar(campo, OBLIGATORIO);
        }
        return this;
    }

    public Validacion nombrePersona(String campo, String valor) {
        if (valor == null) {
            return requerido(campo, null);
        }
        if (valor.length() < 3 || valor.length() > 100) {
            agregar(campo, "Escribe entre 3 y 100 caracteres.");
        } else if (!NOMBRE.matcher(valor).matches()) {
            agregar(campo, "Usa solo letras y espacios.");
        }
        return this;
    }

    public Validacion documento(String campo, String valor) {
        if (valor != null && (valor.length() < 5 || valor.length() > 20 || !DOCUMENTO.matcher(valor).matches())) {
            agregar(campo, "Escribe entre 5 y 20 letras o números, sin espacios.");
        }
        return this;
    }

    public Validacion maximo(String campo, String valor, int maximo) {
        if (valor != null && valor.length() > maximo) {
            agregar(campo, "Escribe máximo " + maximo + " caracteres.");
        }
        return this;
    }

    public Validacion correo(String campo, String valor) {
        if (valor == null) {
            return requerido(campo, null);
        }
        if (valor.length() > 150 || !CORREO.matcher(valor).matches()) {
            agregar(campo, "Escribe un correo válido, por ejemplo nombre@empresa.com.");
        }
        return this;
    }

    public Validacion contrasenaSegura(String campo, String valor) {
        if (valor == null || valor.isEmpty()) {
            return requerido(campo, null);
        }
        if (valor.length() < 8 || valor.length() > 72 || !valor.matches(".*[A-Za-z].*") || !valor.matches(".*\\d.*")) {
            agregar(campo, "Usa al menos 8 caracteres, con letras y números.");
        }
        return this;
    }

    /** Para reglas propias: si la condición es falsa, el campo queda con el mensaje. */
    public Validacion cumple(String campo, boolean condicion, String mensaje) {
        if (!condicion) {
            agregar(campo, mensaje);
        }
        return this;
    }

    public boolean tieneError(String campo) {
        return errores.containsKey(campo);
    }

    public void verificar() {
        if (!errores.isEmpty()) {
            throw new Errores.ErrorValidacion(errores);
        }
    }

    private void agregar(String campo, String mensaje) {
        errores.putIfAbsent(campo, mensaje);
    }
}
