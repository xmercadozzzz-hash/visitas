package co.edu.ucc.visitaregistro;

import co.edu.ucc.visitaregistro.bd.Dialecto;
import co.edu.ucc.visitaregistro.config.Configuracion;
import co.edu.ucc.visitaregistro.seguridad.Contrasenas;
import co.edu.ucc.visitaregistro.servicio.Errores.ErrorValidacion;
import co.edu.ucc.visitaregistro.servicio.Validacion;
import org.junit.Test;

import java.util.Map;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Pruebas de piezas sueltas: configuración, contraseñas y validaciones. */
public class PruebasUnitariasTest {

    @Test
    public void sinVariablesUsaLaBaseLocalHsqldb() {
        Configuracion c = Configuracion.desde(Map.of());
        assertEquals(Dialecto.HSQLDB, c.dialecto());
        assertTrue(c.jdbcUrl().startsWith("jdbc:hsqldb:file:datos/visitaregistro"));
        assertEquals(8080, c.puerto());
        assertEquals("America/Bogota", c.zona().getId());
    }

    @Test
    public void leeLaUrlDeCleverCloud() {
        Configuracion c = Configuracion.desde(Map.of(
            "MYSQL_ADDON_URI", "mysql://usuario1:Cl%40ve99@bf31-mysql.services.clever-cloud.com:22201/bf31base"));
        assertEquals(Dialecto.MYSQL, c.dialecto());
        assertTrue(c.jdbcUrl().startsWith("jdbc:mysql://bf31-mysql.services.clever-cloud.com:22201/bf31base?"));
        assertEquals("usuario1", c.usuarioBd());
        assertEquals("Cl@ve99", c.claveBd());
    }

    @Test
    public void leeVariablesSueltasConEspaciosSobrantes() {
        Configuracion c = Configuracion.desde(Map.of(
            "DB_HOST", " servidor.ejemplo.com ", "DB_PORT", "22201", "DB_NAME", "base1", "DB_USER", "yo", "DB_PASSWORD", "secreta"));
        assertTrue(c.jdbcUrl().startsWith("jdbc:mysql://servidor.ejemplo.com:22201/base1?"));
        assertEquals("yo", c.usuarioBd());
        assertFalse(c.descripcionBd().contains("secreta"));
    }

    @Test
    public void aceptaLosNombresDeVariablesDeCleverCloud() {
        Configuracion c = Configuracion.desde(Map.of(
            "MYSQL_ADDON_HOST", "h.clever-cloud.com", "MYSQL_ADDON_PORT", "3306", "MYSQL_ADDON_DB", "b1",
            "MYSQL_ADDON_USER", "u1", "MYSQL_ADDON_PASSWORD", "p1"));
        assertTrue(c.jdbcUrl().startsWith("jdbc:mysql://h.clever-cloud.com:3306/b1?"));
        assertEquals("p1", c.claveBd());
    }

    @Test
    public void lasContrasenasSeGuardanCifradasYConSal() {
        String a = Contrasenas.cifrar("Recepcion2026");
        String b = Contrasenas.cifrar("Recepcion2026");
        assertTrue(a.startsWith("pbkdf2$"));
        assertFalse(a.contains("Recepcion2026"));
        assertNotEquals("dos cifrados de la misma clave deben ser distintos", a, b);
        assertTrue(Contrasenas.coincide("Recepcion2026", a));
        assertFalse(Contrasenas.coincide("recepcion2026", a));
        assertFalse(Contrasenas.coincide("Recepcion2026", "texto-plano"));
    }

    @Test
    public void validaNombresYDocumentos() {
        Validacion v = new Validacion()
            .nombrePersona("nombre", "Ana")
            .documento("documento", "1023456789")
            .correo("correo", "ana@empresa.com")
            .contrasenaSegura("clave", "Segura2026");
        v.verificar();

        try {
            new Validacion()
                .nombrePersona("nombre", "A1")
                .documento("documento", "12 34")
                .correo("correo", "sin-arroba")
                .contrasenaSegura("clave", "corta")
                .verificar();
            fail("Debía fallar la validación");
        } catch (ErrorValidacion e) {
            assertEquals(4, e.campos().size());
            assertTrue(e.campos().containsKey("documento"));
        }
    }

    @Test
    public void limpiarQuitaEspaciosYCaracteresDeControl() {
        assertEquals("Ana María", Validacion.limpiar("  Ana \n  María  "));
        assertEquals(null, Validacion.limpiar("   "));
    }
}
