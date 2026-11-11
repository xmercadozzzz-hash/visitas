package co.edu.ucc.visitaregistro.web;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.Map;

/** Entrega los archivos de la carpeta web (HTML5, CSS3 y JavaScript del frontend). */
final class ArchivosEstaticos {

    private static final Map<String, String> TIPOS = Map.of(
        "html", "text/html; charset=utf-8",
        "css", "text/css; charset=utf-8",
        "js", "text/javascript; charset=utf-8",
        "svg", "image/svg+xml",
        "png", "image/png",
        "ico", "image/x-icon",
        "json", "application/json; charset=utf-8",
        "txt", "text/plain; charset=utf-8",
        "woff2", "font/woff2"
    );

    private final Path raiz;

    ArchivosEstaticos(Path raiz) {
        this.raiz = raiz.toAbsolutePath().normalize();
    }

    void servir(Intercambio x) throws IOException {
        if (!x.metodo().equals("GET") && !x.metodo().equals("HEAD")) {
            x.crudo().getResponseHeaders().set("Allow", "GET, HEAD");
            x.enviar(405, new byte[0]);
            return;
        }
        String ruta = URLDecoder.decode(x.crudo().getRequestURI().getRawPath(), StandardCharsets.UTF_8);
        Path archivo = raiz.resolve(ruta.equals("/") ? "index.html" : ruta.substring(1)).normalize();

        if (!archivo.startsWith(raiz)) {
            x.enviar(404, "No encontrado".getBytes(StandardCharsets.UTF_8));
            return;
        }
        if (!Files.isRegularFile(archivo)) {
            // Las direcciones sin extensión (#rutas del frontend recargadas) muestran la aplicación
            if (!ultimoSegmento(ruta).contains(".") && Files.isRegularFile(raiz.resolve("index.html"))) {
                archivo = raiz.resolve("index.html");
            } else {
                x.crudo().getResponseHeaders().set("Content-Type", "text/plain; charset=utf-8");
                x.enviar(404, "No encontrado".getBytes(StandardCharsets.UTF_8));
                return;
            }
        }

        Instant modificado = Files.getLastModifiedTime(archivo).toInstant().truncatedTo(ChronoUnit.SECONDS);
        var cabeceras = x.crudo().getResponseHeaders();
        cabeceras.set("Content-Type", TIPOS.getOrDefault(extension(archivo), "application/octet-stream"));
        cabeceras.set("Cache-Control", "no-cache");
        cabeceras.set("Last-Modified", DateTimeFormatter.RFC_1123_DATE_TIME.format(modificado.atZone(ZoneOffset.UTC)));

        String desde = x.cabecera("If-Modified-Since");
        if (desde != null) {
            try {
                Instant cliente = ZonedDateTime.parse(desde, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant();
                if (!modificado.isAfter(cliente)) {
                    x.enviar(304, new byte[0]);
                    return;
                }
            } catch (DateTimeParseException ignorada) {
                // fecha inválida: se envía el archivo completo
            }
        }
        x.enviar(200, Files.readAllBytes(archivo));
    }

    private static String ultimoSegmento(String ruta) {
        return ruta.substring(ruta.lastIndexOf('/') + 1);
    }

    private static String extension(Path archivo) {
        String nombre = archivo.getFileName().toString();
        int punto = nombre.lastIndexOf('.');
        return punto < 0 ? "" : nombre.substring(punto + 1).toLowerCase();
    }
}
