package co.edu.ucc.visitaregistro.modelo;

/** Los tres tipos de usuario definidos en el documento de requerimientos. */
public enum Rol {

    RECEPCIONISTA("Recepcionista"),
    ANFITRION("Empleado anfitrión"),
    ADMINISTRADOR("Administrador de recepción");

    private final String etiqueta;

    Rol(String etiqueta) {
        this.etiqueta = etiqueta;
    }

    public String etiqueta() {
        return etiqueta;
    }

    /** Acepta el código (RECEPCIONISTA) o la etiqueta visible (Recepcionista). */
    public static Rol desdeTexto(String texto) {
        if (texto == null) {
            return null;
        }
        for (Rol rol : values()) {
            if (rol.name().equalsIgnoreCase(texto.trim()) || rol.etiqueta.equalsIgnoreCase(texto.trim())) {
                return rol;
            }
        }
        return null;
    }
}
