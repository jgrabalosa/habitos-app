package com.norday;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Properties;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Un idioma a medio traducir no rompe nada (hay caída al bundle base), pero
 * manda al usuario un email mezclando dos idiomas. Esto lo detecta antes.
 */
class BundlesMensajesTest {

    private static final List<String> IDIOMAS = List.of("en", "pt");
    private static final List<String> MODULOS = List.of("core", "habitos");

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
}
