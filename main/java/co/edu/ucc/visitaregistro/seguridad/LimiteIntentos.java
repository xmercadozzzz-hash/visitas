package co.edu.ucc.visitaregistro.seguridad;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Bloquea temporalmente el inicio de sesión después de varios intentos
 * fallidos seguidos desde el mismo equipo para el mismo correo.
 */
public final class LimiteIntentos {

    private static final int MAXIMO_FALLOS = 5;
    private static final Duration VENTANA = Duration.ofMinutes(15);
    private static final Duration BLOQUEO = Duration.ofMinutes(5);

    private record Registro(int fallos, Instant primerFallo, Instant bloqueadoHasta) {
    }

    private final Map<String, Registro> registros = new ConcurrentHashMap<>();

    /** Minutos que faltan de bloqueo, o 0 si puede intentar. */
    public long minutosBloqueado(String correo, String ip) {
        Registro r = registros.get(clave(correo, ip));
        if (r == null || r.bloqueadoHasta() == null) {
            return 0;
        }
        Duration falta = Duration.between(Instant.now(), r.bloqueadoHasta());
        if (falta.isNegative()) {
            registros.remove(clave(correo, ip));
            return 0;
        }
        return Math.max(1, (falta.toSeconds() + 59) / 60);
    }

    public void fallo(String correo, String ip) {
        Instant ahora = Instant.now();
        registros.compute(clave(correo, ip), (k, r) -> {
            if (r == null || r.primerFallo().plus(VENTANA).isBefore(ahora)) {
                return new Registro(1, ahora, null);
            }
            int fallos = r.fallos() + 1;
            return new Registro(fallos, r.primerFallo(), fallos >= MAXIMO_FALLOS ? ahora.plus(BLOQUEO) : null);
        });
    }

    public void exito(String correo, String ip) {
        registros.remove(clave(correo, ip));
    }

    private static String clave(String correo, String ip) {
        return (correo == null ? "" : correo.trim().toLowerCase()) + "|" + ip;
    }
}
