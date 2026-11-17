package com.norday;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El bundle de hábitos en portugués está en portugués de Portugal (tens,
 * tua, podes...). Las claves de los hábitos en minutos se escribieron al
 * principio en portugués de Brasil; este test lo impide.
 */
class MensajesPortuguesTest {

    /** Formas de Brasil que en Portugal se dicen de otra manera. */
    private static final List<String> DE_BRASIL = List.of(
            "Atualize", "Tente", "registrar", "registro", "seus", "suas", "em outro", "outro lugar");

    @Test
    void lasClavesDeHabitosEstanEnPortuguesDePortugal() throws IOException {
        Properties pt = new Properties();
        try (InputStream in = getClass().getResourceAsStream("/mensajes/habitos_pt.properties")) {
            assertNotNull(in);
            pt.load(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        List<String> mal = new ArrayList<>();
        for (String clave : pt.stringPropertyNames()) {
            String texto = pt.getProperty(clave);
            for (String forma : DE_BRASIL) {
                if (texto.contains(forma)) mal.add(clave + " («" + forma + "»)");
            }
        }
        assertEquals(List.of(), mal);
    }
}
