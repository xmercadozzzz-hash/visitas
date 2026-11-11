import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

/**
 * Descarga las librerías que usa el proyecto desde Maven Central a la carpeta lib/
 * y comprueba su huella SHA-1. Así no hace falta instalar Maven.
 *
 *   java herramientas/DescargarLibrerias.java            (librerías del sistema)
 *   java herramientas/DescargarLibrerias.java pruebas    (además, JUnit para las pruebas)
 */
public class DescargarLibrerias {

    record Libreria(String grupo, String artefacto, String version) {
        String archivo() {
            return artefacto + "-" + version + ".jar";
        }

        String url() {
            return "https://repo1.maven.org/maven2/" + grupo.replace('.', '/') + "/" + artefacto + "/" + version + "/" + archivo();
        }
    }

    static final List<Libreria> SISTEMA = List.of(
        new Libreria("com.google.code.gson", "gson", "2.13.1"),        // JSON
        new Libreria("com.mysql", "mysql-connector-j", "8.4.0"),        // MySQL (producción)
        new Libreria("org.hsqldb", "hsqldb", "2.7.3")                   // base embebida (computador local)
    );

    static final List<Libreria> PRUEBAS = List.of(
        new Libreria("junit", "junit", "4.13.2"),
        new Libreria("org.hamcrest", "hamcrest-core", "1.3")
    );

    public static void main(String[] args) {
        try {
            preparar(args);
        } catch (Exception e) {
            System.err.println();
            System.err.println("ERROR: " + (e.getMessage() == null ? e : e.getMessage()));
            System.err.println("Las librerías se descargan de internet solo la primera vez.");
            System.exit(1);
        }
    }

    private static void preparar(String[] args) throws Exception {
        List<Libreria> lista = new ArrayList<>(SISTEMA);
        if (args.length > 0 && args[0].equals("pruebas")) {
            lista.addAll(PRUEBAS);
        }
        Path carpeta = Path.of("lib");
        Files.createDirectories(carpeta);
        HttpClient http = HttpClient.newBuilder()
            .proxy(ProxySelector.getDefault())
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

        for (Libreria lib : lista) {
            Path destino = carpeta.resolve(lib.archivo());
            if (Files.exists(destino) && Files.size(destino) > 0) {
                continue;
            }
            System.out.println("Descargando " + lib.archivo() + " ...");
            byte[] jar = descargar(http, lib.url());
            String esperado = new String(descargar(http, lib.url() + ".sha1")).trim().split("\\s+")[0];
            String obtenido = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-1").digest(jar));
            if (!obtenido.equalsIgnoreCase(esperado)) {
                throw new IllegalStateException("La descarga de " + lib.archivo() + " llegó dañada (SHA-1 distinto).");
            }
            Files.write(destino, jar);
        }
        System.out.println("Librerías listas en la carpeta lib.");
    }

    private static byte[] descargar(HttpClient http, String url) throws Exception {
        HttpResponse<byte[]> r;
        try {
            r = http.send(HttpRequest.newBuilder(URI.create(url)).build(), HttpResponse.BodyHandlers.ofByteArray());
        } catch (java.io.IOException e) {
            throw new IllegalStateException("No hay conexión con Maven Central para descargar " + url + ". Revisa tu internet.");
        }
        if (r.statusCode() != 200) {
            throw new IllegalStateException("No se pudo descargar " + url + " (código " + r.statusCode() + "). Revisa tu conexión a internet.");
        }
        return r.body();
    }
}
