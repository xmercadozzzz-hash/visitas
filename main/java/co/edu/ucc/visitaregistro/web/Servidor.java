package co.edu.ucc.visitaregistro.web;

import co.edu.ucc.visitaregistro.seguridad.Sesiones;
import co.edu.ucc.visitaregistro.seguridad.Sesiones.Sesion;
import co.edu.ucc.visitaregistro.servicio.Errores.ErrorNegocio;
import co.edu.ucc.visitaregistro.servicio.Errores.ErrorValidacion;
import co.edu.ucc.visitaregistro.servicio.Vistas.ErrorVista;
import co.edu.ucc.visitaregistro.web.Enrutador.Acceso;
import co.edu.ucc.visitaregistro.web.Enrutador.Coincidencia;
import co.edu.ucc.visitaregistro.web.Enrutador.Respuesta;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.sql.SQLException;
import java.util.concurrent.Executors;

/**
 * Servidor HTTP del sistema (incluido en Java, sin frameworks). Atiende:
 *  - /api/...  la API en JSON que usa el frontend
 *  - lo demás  los archivos HTML, CSS y JavaScript de la carpeta web
 */
public final class Servidor {

    /** Cabecera que el frontend envía en las peticiones que modifican datos (protección CSRF). */
    public static final String CABECERA_CSRF = "X-Requested-With";
    public static final String VALOR_CSRF = "VisitaRegistro";

    private static final String POLITICA_CONTENIDO = "default-src 'self'; script-src 'self'; "
        + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; font-src 'self' https://fonts.gstatic.com; "
        + "img-src 'self' data:; connect-src 'self'; frame-ancestors 'none'; base-uri 'self'; form-action 'self'";

    private final HttpServer http;
    private final Enrutador enrutador;
    private final Sesiones sesiones;
    private final ArchivosEstaticos estaticos;

    public Servidor(int puerto, Path carpetaWeb, Enrutador enrutador, Sesiones sesiones) throws IOException {
        this.enrutador = enrutador;
        this.sesiones = sesiones;
        this.estaticos = new ArchivosEstaticos(carpetaWeb);
        this.http = HttpServer.create(new InetSocketAddress(puerto), 0);
        this.http.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        this.http.createContext("/", this::atender);
    }

    public void iniciar() {
        http.start();
    }

    public int puerto() {
        return http.getAddress().getPort();
    }

    public void detener() {
        http.stop(1);
    }

    private void atender(HttpExchange ex) {
        Intercambio x = new Intercambio(ex);
        try {
            var cabeceras = ex.getResponseHeaders();
            cabeceras.set("X-Content-Type-Options", "nosniff");
            cabeceras.set("X-Frame-Options", "DENY");
            cabeceras.set("Referrer-Policy", "same-origin");
            cabeceras.set("Content-Security-Policy", POLITICA_CONTENIDO);

            if (x.ruta().startsWith("/api/")) {
                atenderApi(x);
            } else {
                estaticos.servir(x);
            }
        } catch (Exception e) {
            System.err.println("[error] " + x.metodo() + " " + x.ruta() + ": " + e);
            responderSinFallar(x, 500, new ErrorVista("Ocurrió un error inesperado. Intenta de nuevo.", null));
        } finally {
            ex.close();
        }
    }

    private void atenderApi(Intercambio x) throws IOException {
        Coincidencia coincidencia = enrutador.buscar(x);
        if (coincidencia == null) {
            x.enviarJson(404, new ErrorVista("La ruta no existe.", null));
            return;
        }
        if (coincidencia.metodoDistinto()) {
            x.enviarJson(405, new ErrorVista("Método no permitido.", null));
            return;
        }
        boolean modifica = !x.metodo().equals("GET") && !x.metodo().equals("HEAD");
        if (modifica && !VALOR_CSRF.equals(x.cabecera(CABECERA_CSRF))) {
            x.enviarJson(403, new ErrorVista("Petición rechazada por seguridad.", null));
            return;
        }

        Sesion sesion = sesiones.obtener(x.cookie(Sesiones.COOKIE));
        if (coincidencia.ruta().acceso() == Acceso.CON_SESION && sesion == null) {
            x.enviarJson(401, new ErrorVista("Tu sesión terminó. Vuelve a iniciar sesión.", null));
            return;
        }

        try {
            Object resultado = coincidencia.ruta().manejador().atender(x, sesion);
            if (resultado instanceof Respuesta r) {
                x.enviarJson(r.estado(), r.cuerpo());
            } else {
                x.enviarJson(200, resultado);
            }
        } catch (ErrorValidacion e) {
            x.enviarJson(400, new ErrorVista(e.getMessage(), e.campos()));
        } catch (ErrorNegocio e) {
            x.enviarJson(e.estadoHttp(), new ErrorVista(e.getMessage(), null));
        } catch (SQLException e) {
            System.err.println("[base de datos] " + x.metodo() + " " + x.ruta() + ": " + e.getMessage());
            x.enviarJson(503, new ErrorVista("No se pudo conectar con la base de datos. Intenta de nuevo en unos segundos.", null));
        } catch (Exception e) {
            System.err.println("[error] " + x.metodo() + " " + x.ruta() + ": " + e);
            e.printStackTrace();
            x.enviarJson(500, new ErrorVista("Ocurrió un error inesperado. Intenta de nuevo.", null));
        }
    }

    private static void responderSinFallar(Intercambio x, int estado, Object cuerpo) {
        try {
            x.enviarJson(estado, cuerpo);
        } catch (IOException | RuntimeException ignorada) {
            // la respuesta ya había empezado o el cliente se desconectó
        }
    }
}
