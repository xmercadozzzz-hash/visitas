package co.edu.ucc.visitaregistro.seguridad;

import co.edu.ucc.visitaregistro.modelo.Modelos.Usuario;
import co.edu.ucc.visitaregistro.modelo.Rol;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Sesiones de los usuarios que iniciaron sesión. El navegador solo guarda un
 * identificador aleatorio en una cookie HttpOnly; los datos quedan en el servidor.
 */
public final class Sesiones {

    public static final String COOKIE = "VR_SESION";
    private static final Duration DURACION_MAXIMA = Duration.ofHours(12);
    private static final SecureRandom ALEATORIO = new SecureRandom();

    /** Lo que el servidor recuerda de cada usuario conectado. */
    public static final class Sesion {
        private final String token;
        private final int usuarioId;
        private final String nombre;
        private final String correo;
        private final Rol rol;
        private final Integer anfitrionId;
        private final Instant creada = Instant.now();
        private volatile Instant ultimoUso = Instant.now();

        Sesion(String token, Usuario usuario) {
            this.token = token;
            this.usuarioId = usuario.id();
            this.nombre = usuario.nombre();
            this.correo = usuario.correo();
            this.rol = usuario.rol();
            this.anfitrionId = usuario.anfitrionId();
        }

        public String token() {
            return token;
        }

        public int usuarioId() {
            return usuarioId;
        }

        public String nombre() {
            return nombre;
        }

        public String correo() {
            return correo;
        }

        public Rol rol() {
            return rol;
        }

        public Integer anfitrionId() {
            return anfitrionId;
        }

        public boolean es(Rol... roles) {
            for (Rol r : roles) {
                if (r == rol) {
                    return true;
                }
            }
            return false;
        }
    }

    private final Map<String, Sesion> activas = new ConcurrentHashMap<>();
    private final Duration inactividadMaxima;

    public Sesiones(int minutosInactividad) {
        this.inactividadMaxima = Duration.ofMinutes(Math.max(5, minutosInactividad));
    }

    public Sesion crear(Usuario usuario) {
        byte[] bytes = new byte[32];
        ALEATORIO.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        Sesion sesion = new Sesion(token, usuario);
        activas.put(token, sesion);
        return sesion;
    }

    /** Devuelve la sesión si existe y no ha vencido; cada uso la renueva. */
    public Sesion obtener(String token) {
        if (token == null) {
            return null;
        }
        Sesion sesion = activas.get(token);
        if (sesion == null) {
            return null;
        }
        if (vencida(sesion, Instant.now())) {
            activas.remove(token);
            return null;
        }
        sesion.ultimoUso = Instant.now();
        return sesion;
    }

    public void cerrar(String token) {
        if (token != null) {
            activas.remove(token);
        }
    }

    /** Obliga a un usuario a volver a entrar (por ejemplo, si el administrador lo desactiva o le cambia el rol). */
    public void cerrarDeUsuario(int usuarioId) {
        activas.values().removeIf(s -> s.usuarioId == usuarioId);
    }

    public void limpiarVencidas() {
        Instant ahora = Instant.now();
        activas.values().removeIf(s -> vencida(s, ahora));
    }

    public long maxAgeSegundos() {
        return DURACION_MAXIMA.toSeconds();
    }

    private boolean vencida(Sesion s, Instant ahora) {
        return s.ultimoUso.plus(inactividadMaxima).isBefore(ahora) || s.creada.plus(DURACION_MAXIMA).isBefore(ahora);
    }
}
