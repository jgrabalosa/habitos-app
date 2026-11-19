package com.norday;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Un fichero que no existe es un 404, no un 500. Los robots de internet piden
 * cosas como /js/config.js; caían en el manejador de «Excepción no
 * controlada» y llenaban el log de errores con traza (staging, 19-nov-2026).
 */
@SpringBootTest
@AutoConfigureMockMvc
@ExtendWith(OutputCaptureExtension.class)
class RecursoInexistenteTest {

    @Autowired
    private MockMvc mockMvc;

    private int estado(String ruta) throws Exception {
        return mockMvc.perform(get(ruta)).andReturn().getResponse().getStatus();
    }

    @Test
    void unScriptQueNoExiste_es404_yNoSaleComoError(CapturedOutput salida) throws Exception {
        assertEquals(404, estado("/js/config.js"));
        assertFalse(salida.getAll().contains("Excepción no controlada"),
                "un fichero que no existe no es un fallo del servidor");
    }

    @Test
    void unaImagenQueNoExiste_es404() throws Exception {
        assertEquals(404, estado("/img/no-existe.png"));
    }

    @Test
    void elScriptDeLaLanding_sigueSaliendo() throws Exception {
        assertEquals(200, estado("/js/landing.js"));
    }
}
