package com.norday;

import com.norday.enfoque.model.MotivosEnfoque;
import com.norday.habitos.model.CalculoAvisos;
import com.norday.habitos.model.TipoAviso;
import com.norday.habitos.service.AvisosAppScheduler;
import com.norday.habitos.service.DestinoMinutosHabitos;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.text.MessageFormat;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Un idioma a medio traducir no rompe nada (hay caída al bundle base), pero
 * manda al usuario un email mezclando dos idiomas. Esto lo detecta antes.
 */
class BundlesMensajesTest {

    private static final List<String> IDIOMAS = List.of("en", "pt");
    private static final List<String> MODULOS = List.of("core", "habitos", "enfoque");

    private Properties cargar(String recurso) throws IOException {
        Properties props = new Properties();
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(recurso)) {
            assertNotNull(in, "Falta el bundle " + recurso);
            props.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        return props;
    }

    @Test
    void cadaIdiomaTraduceExactamenteLasMismasClavesQueElBase() throws IOException {
        for (String modulo : MODULOS) {
            Set<String> base = new TreeSet<>(cargar("mensajes/" + modulo + ".properties").stringPropertyNames());
            assertFalse(base.isEmpty(), "El bundle base de " + modulo + " está vacío");

            for (String idioma : IDIOMAS) {
                Set<String> traducido =
                        new TreeSet<>(cargar("mensajes/" + modulo + "_" + idioma + ".properties").stringPropertyNames());

                Set<String> faltan = new TreeSet<>(base);
                faltan.removeAll(traducido);
                assertTrue(faltan.isEmpty(),
                        "Sin traducir en " + modulo + "_" + idioma + ": " + faltan);

                Set<String> sobran = new TreeSet<>(traducido);
                sobran.removeAll(base);
                assertTrue(sobran.isEmpty(),
                        "Claves en " + modulo + "_" + idioma + " que no existen en el base: " + sobran);
            }
        }
    }

    @Test
    void elMotorNoContieneTextoDeLaAppDeHabitos() throws IOException {
        // La bienvenida y los push son contenido de esta app: van en su bundle,
        // no en el del motor, o se arrastra la violación a todo el ecosistema.
        Set<String> clavesCore = cargar("mensajes/core.properties").stringPropertyNames();

        assertTrue(clavesCore.stream().noneMatch(c -> c.startsWith("push.")),
                "El motor no debe llevar textos de push de una app concreta");
        assertTrue(clavesCore.stream().noneMatch(c -> c.startsWith("email.bienvenida")),
                "La bienvenida habla de hábitos: pertenece al bundle de la app");
    }

    @Test
    void cadaAvisoDeLaApp_tieneTodasSusVariantes_enLosTresIdiomas() throws IOException {
        // CalculoAvisos.VARIANTES dice cuántos textos hay de cada aviso; si el
        // bundle tiene menos, el barrido pediría una clave que no existe.
        for (String recurso : List.of("habitos", "habitos_en", "habitos_pt")) {
            Properties props = cargar("mensajes/" + recurso + ".properties");
            for (TipoAviso tipo : TipoAviso.values()) {
                int variantes = CalculoAvisos.VARIANTES.get(tipo);
                List<String> bases = tipo == TipoAviso.MOMENTO_DEBIL
                        ? List.of(AvisosAppScheduler.claveBase(tipo, 1), AvisosAppScheduler.claveBase(tipo, 2))
                        : List.of(AvisosAppScheduler.claveBase(tipo, 0));
                for (String base : bases) {
                    for (int n = 1; n <= variantes; n++) {
                        for (String parte : List.of("titulo", "cuerpo")) {
                            String clave = base + "." + n + "." + parte;
                            String texto = props.getProperty(clave);
                            assertNotNull(texto, "Falta " + clave + " en " + recurso);
                            assertFalse(texto.isBlank(), "Vacía: " + clave + " en " + recurso);
                        }
                    }
                }
            }
        }
    }

    @Test
    void losAvisosDeLaApp_tratanBienElApostrofo() throws IOException {
        // Las claves del momento débil se leen siempre con argumentos, así que
        // pasan por MessageFormat: un apóstrofo simple se come lo que sigue,
        // incluido el {0}. Las de vuelta se leen tal cual: un '' saldría doble.
        for (String recurso : List.of("habitos", "habitos_en", "habitos_pt")) {
            Properties props = cargar("mensajes/" + recurso + ".properties");
            for (String clave : props.stringPropertyNames()) {
                if (!clave.startsWith("push.aviso.")) continue;
                String texto = props.getProperty(clave);
                if (clave.startsWith("push.aviso.momentodebil.")) {
                    assertFalse(Pattern.compile("(?<!')'(?!')").matcher(texto).find(),
                            "Apóstrofo sin doblar en " + clave + " (" + recurso + ")");
                    if (texto.contains("{0}")) {
                        assertTrue(new MessageFormat(texto).format(new Object[]{"Leer"}).contains("Leer"),
                                "Se pierde el argumento en " + clave + " (" + recurso + ")");
                    }
                } else {
                    assertFalse(texto.contains("''"), "Apóstrofo doblado en " + clave + " (" + recurso + ")");
                    assertFalse(texto.contains("{"), "Una clave de vuelta no lleva argumentos: " + clave);
                }
            }
        }
    }

    @Test
    void losMotivosDeRechazoDeUnAporte_tienenTexto_yCabenEnLaColumna() throws IOException {
        // Los motivos viajan como clave y la app enseña su texto: cada uno
        // tiene que estar en el bundle de quien lo pone (los idiomas los
        // vigila el test de arriba) y caber en sesion_enfoque.aporte_motivo.
        Set<String> habitos = cargar("mensajes/habitos.properties").stringPropertyNames();
        for (String motivo : DestinoMinutosHabitos.MOTIVOS) {
            assertTrue(habitos.contains(motivo), "Sin texto en habitos: " + motivo);
            assertTrue(motivo.length() <= 40, "No cabe en aporte_motivo: " + motivo);
        }
        Set<String> enfoque = cargar("mensajes/enfoque.properties").stringPropertyNames();
        for (String motivo : List.of(MotivosEnfoque.SESION_PERDIDA, MotivosEnfoque.DESTINO_DESCONOCIDO)) {
            assertTrue(enfoque.contains(motivo), "Sin texto en enfoque: " + motivo);
            assertTrue(motivo.length() <= 40, "No cabe en aporte_motivo: " + motivo);
        }
    }
}
