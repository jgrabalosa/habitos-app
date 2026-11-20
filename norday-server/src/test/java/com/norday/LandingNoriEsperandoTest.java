package com.norday;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Línea de marketing M-4: cuando el usuario no vuelve, Nori «te espera»; nunca
 * «te ha echado de menos», que suena a reproche. No levanta Spring.
 */
class LandingNoriEsperandoTest {

    private String html() throws IOException {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream("static/index.html")) {
            assertNotNull(in, "Falta static/index.html");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void noriTeEstaEsperando_yNadaEchaDeMenos() throws IOException {
        String html = html();
        assertTrue(html.contains("Nori te estará esperando"),
                "La landing debe decir «Nori te estará esperando»");
        assertFalse(html.contains("echado de menos"),
                "La landing no debe decir «echado de menos»");
    }
}
