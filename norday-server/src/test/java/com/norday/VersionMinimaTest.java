package com.norday;

import com.norday.core.model.VersionApp;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La app pregunta su versión mínima antes del login, así que el endpoint es
 * público. Si la app no tiene fila, la mínima es 0: un dato que falta nunca
 * debe bloquear a nadie.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class VersionMinimaTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private EntityManager em;

    @Test
    void sinSesion_appConFila_devuelveSuVersionMinima() throws Exception {
        em.persist(new VersionApp("habitos", 5));
        em.flush();

        mockMvc.perform(get("/api/version/habitos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionMinima").value(5));
    }

    @Test
    void sinSesion_appSinFila_devuelveCero() throws Exception {
        mockMvc.perform(get("/api/version/otra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.versionMinima").value(0));
    }
}
