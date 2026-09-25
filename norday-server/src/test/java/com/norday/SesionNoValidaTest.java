package com.norday;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

/**
 * Sin sesión válida el backend responde 401, no 403. La app usa esa
 * diferencia: ante un 401 cierra la sesión y manda al login («tu sesión ha
 * caducado»); un 403 significa que se pidió algo de otro usuario
 * (AutorizacionPorUsuarioTest) y no debe echar a nadie.
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionNoValidaTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void sinToken_devuelve401() throws Exception {
        int estado = mockMvc.perform(get("/api/usuarios/1"))
                .andReturn().getResponse().getStatus();
        assertEquals(401, estado);
    }

    @Test
    void conTokenMalFirmado_devuelve401() throws Exception {
        int estado = mockMvc.perform(get("/api/usuarios/1")
                        .header("Authorization", "Bearer token.no.valido"))
                .andReturn().getResponse().getStatus();
        assertEquals(401, estado);
    }
}
