package co.edu.ucc.visitaregistro.web;

import co.edu.ucc.visitaregistro.servicio.Errores.ErrorNegocio;
import co.edu.ucc.visitaregistro.servicio.Validacion;
import co.edu.ucc.visitaregistro.util.Json;
import com.google.gson.JsonParseException;
import com.sun.net.httpserver.HttpExchange;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

/** Una petición HTTP y su respuesta, con ayudas para leer parámetros, JSON y cookies. */
public final class Intercambio {

    private static final int MAXIMO_CUERPO = 64 * 1024;

    private final HttpExchange ex;
    private final Map<String, String> parametrosRuta = new HashMap<>();
    private Map<String, String> consulta;

    public Intercambio(HttpExchange ex) {
        this.ex = ex;
    }

    public String metodo() {
        return ex.getRequestMethod().toUpperCase();
    }

    public String ruta() {
        return ex.getRequestURI().getPath();
    }

    void parametroRuta(String nombre, String valor) {
        parametrosRuta.put(nombre, valor);
    }

    /** Id numérico tomado de la ruta, por ejemplo /api/visitas/{id}. */
    public int id() {
        return Integer.parseInt(parametrosRuta.get("id"));
    }

    /** Parámetro de la consulta (?nombre=valor), o null si no viene. */
    public String parametro(String nombre) {
        if (consulta == null) {
            consulta = new HashMap<>();
            String crudo = ex.getRequestURI().getRawQuery();
            if (crudo != null) {
                for (String par : crudo.split("&")) {
                    String[] kv = par.split("=", 2);
                    String clave = URLDecoder.decode(kv[0], StandardCharsets.UTF_8);
                    String valor = kv.length > 1 ? URLDecoder.decode(kv[1], StandardCharsets.UTF_8) : "";
                    consulta.putIfAbsent(clave, valor);
                }
            }
        }
        String valor = consulta.get(nombre);
        return valor == null || valor.isBlank() ? null : valor.trim();
    }

    public Integer parametroEntero(String nombre) {
        String valor = parametro(nombre);
        if (valor == null) {
            return null;
        }
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            new Validacion().cumple(nombre, false, "Valor no válido.").verificar();
            return null;
        }
    }

    /** Lee el cuerpo JSON de la petición y lo convierte a la clase indicada. */
    public <T> T cuerpo(Class<T> clase) throws IOException {
        byte[] datos;
        try (InputStream in = ex.getRequestBody()) {
            datos = in.readNBytes(MAXIMO_CUERPO + 1);
        }
        if (datos.length > MAXIMO_CUERPO) {
            throw new ErrorNegocio(413, "La información enviada es demasiado grande.");
        }
        try {
            T objeto = Json.GSON.fromJson(new String(datos, StandardCharsets.UTF_8), clase);
            if (objeto == null) {
                throw new ErrorNegocio(400, "La petición no tiene datos.");
            }
            return objeto;
        } catch (JsonParseException | NumberFormatException e) {
            throw new ErrorNegocio(400, "Los datos enviados no tienen el formato correcto.");
        }
    }

    public String cabecera(String nombre) {
        return ex.getRequestHeaders().getFirst(nombre);
    }

    public String cookie(String nombre) {
        String cookies = cabecera("Cookie");
        if (cookies == null) {
            return null;
        }
        for (String parte : cookies.split(";")) {
            String[] kv = parte.trim().split("=", 2);
            if (kv.length == 2 && kv[0].equals(nombre)) {
                return kv[1];
            }
        }
        return null;
    }

    /** IP del cliente; detrás del proxy de Render llega en X-Forwarded-For. */
    public String ip() {
        String reenviada = cabecera("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            return reenviada.split(",")[0].trim();
        }
        return ex.getRemoteAddress().getAddress().getHostAddress();
    }

    public boolean esHttps() {
        return "https".equalsIgnoreCase(cabecera("X-Forwarded-Proto"));
    }

    public void ponerCookie(String valorCompleto) {
        ex.getResponseHeaders().add("Set-Cookie", valorCompleto);
    }

    public void enviarJson(int estado, Object cuerpo) throws IOException {
        byte[] datos = Json.GSON.toJson(cuerpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().set("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().set("Cache-Control", "no-store");
        enviar(estado, datos);
    }

    void enviar(int estado, byte[] datos) throws IOException {
        boolean sinCuerpo = metodo().equals("HEAD") || estado == 204 || estado == 304;
        ex.sendResponseHeaders(estado, sinCuerpo ? -1 : datos.length);
        if (!sinCuerpo) {
            try (OutputStream out = ex.getResponseBody()) {
                out.write(datos);
            }
        }
    }

    HttpExchange crudo() {
        return ex;
    }
}
