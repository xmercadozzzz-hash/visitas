package co.edu.ucc.visitaregistro.seguridad;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Las contraseñas nunca se guardan en texto plano: se guarda un hash
 * PBKDF2-SHA256 con sal aleatoria (algoritmo incluido en Java).
 * Formato guardado: pbkdf2$iteraciones$sal$hash
 */
public final class Contrasenas {

    private static final int ITERACIONES = 120_000;
    private static final int LONGITUD_BITS = 256;
    private static final SecureRandom ALEATORIO = new SecureRandom();
    private static final String HASH_FALSO = cifrar("contrasena-que-no-existe-1");

    private Contrasenas() {
    }

    public static String cifrar(String contrasena) {
        byte[] sal = new byte[16];
        ALEATORIO.nextBytes(sal);
        byte[] hash = derivar(contrasena, sal, ITERACIONES);
        Base64.Encoder b64 = Base64.getEncoder();
        return "pbkdf2$" + ITERACIONES + "$" + b64.encodeToString(sal) + "$" + b64.encodeToString(hash);
    }

    public static boolean coincide(String contrasena, String guardado) {
        if (contrasena == null || guardado == null) {
            return false;
        }
        String[] partes = guardado.split("\\$");
        if (partes.length != 4 || !partes[0].equals("pbkdf2")) {
            return false;
        }
        try {
            int iteraciones = Integer.parseInt(partes[1]);
            byte[] sal = Base64.getDecoder().decode(partes[2]);
            byte[] esperado = Base64.getDecoder().decode(partes[3]);
            return MessageDigest.isEqual(esperado, derivar(contrasena, sal, iteraciones));
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    /** Gasta el mismo tiempo que una verificación real (para no revelar qué correos existen). */
    public static void simularVerificacion(String contrasena) {
        coincide(contrasena, HASH_FALSO);
    }

    /** Contraseña aleatoria legible, para cuentas creadas automáticamente. */
    public static String generar() {
        String letras = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 14; i++) {
            sb.append(letras.charAt(ALEATORIO.nextInt(letras.length())));
        }
        return sb.append("7*").toString();
    }

    private static byte[] derivar(String contrasena, byte[] sal, int iteraciones) {
        try {
            PBEKeySpec spec = new PBEKeySpec(contrasena.toCharArray(), sal, iteraciones, LONGITUD_BITS);
            return SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).getEncoded();
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("PBKDF2 no está disponible en esta versión de Java", e);
        }
    }
}
