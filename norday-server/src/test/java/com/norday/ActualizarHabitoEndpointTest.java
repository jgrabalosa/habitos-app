package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.RachaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La respuesta del PUT de un hábito de punta a punta: sigue siendo un 200 —la
 * app ya instalada solo mira el código— y dice desde cuándo se aplica el
 * cambio en {@code aplicaDesde}.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ActualizarHabitoEndpointTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private int habitoId;
    private String token;

    /** Un diario de una vez al día, hecho hoy. */
    @BeforeEach
    void unDiarioHechoHoy() {
        String email = "test-put-" + System.nanoTime() + "@example.com";
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Put");
        usuario.setUsername("test-put-" + System.nanoTime());
        usuario.setEmail(email);
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito leer = new Habito("Leer", "Descripción", Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.of(2026, 1, 1));
        em.persist(leer);
        em.persist(new Racha(leer, VIERNES));
        em.persist(new Ritmo(leer, VIERNES));
        em.persist(new Registro(leer, true, "", VIERNES));
        em.flush();
        em.clear();

        habitoId = leer.getHabitoId();
        token = jwtUtil.generateToken(usuario.getUsuarioId(), email);
        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    private String cuerpo(int meta) {
        return "{\"nombre\":\"Leer\",\"descripcion\":\"Descripción\",\"frecuencia\":\"DIARIO\","
                + "\"meta\":" + meta + ",\"diasSemana\":null,\"recordatorioActivo\":true,"
                + "\"recordatorioHora\":null}";
    }

    @Test
    void subirLaMetaConRegistrosHoy_respondeQueSeAplicaManana() throws Exception {
        mockMvc.perform(put("/api/habitos/" + habitoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Hábito actualizado correctamente"))
                .andExpect(jsonPath("$.aplicaDesde").value("2026-10-10"));
    }

    @Test
    void sinCambiarElObjetivo_respondeQueSeAplicaHoy() throws Exception {
        mockMvc.perform(put("/api/habitos/" + habitoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo(1)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaDesde").value("2026-10-09"));
    }
}
