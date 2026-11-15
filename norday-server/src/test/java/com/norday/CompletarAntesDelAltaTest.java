package com.norday;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
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
import java.util.List;
import java.util.Map;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * No se puede completar un día anterior al alta del hábito. El suelo del
 * completado retroactivo es el lunes de la semana en curso, pero un hábito
 * creado el viernes no existía de lunes a jueves: marcar esos días el mismo
 * viernes cobraba los pasos, el hito de 3 días y RACHA_3 el día del alta.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompletarAntesDelAltaTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate VIERNES = LUNES.plusDays(4);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RegistroService registroService;
    @Autowired private IRegistroDAO registroDAO;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;

    private Habito diarioCreadoEl(LocalDate alta, String idioma) {
        usuario = new Usuario();
        usuario.setNombre("Test Antes del Alta");
        usuario.setUsername("test-alta-" + System.nanoTime());
        usuario.setEmail("test-alta-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma(idioma);
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
        Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1, usuario, null, alta);
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, alta, new Objetivo(Frecuencia.DIARIO, 1, null)));
        em.persist(new Racha(habito, alta));
        em.persist(new Ritmo(habito, alta));
        em.flush();
        return habito;
    }

    private Map<String, Object> paso(Habito habito, LocalDate fecha) {
        em.flush();
        em.clear();
        return registroService.completar(em.find(Habito.class, habito.getHabitoId()),
                new Solicitud(null, fecha, null, null, OrigenRegistro.MANUAL));
    }

    @Test
    void creadoElViernes_noAceptaDeLunesAJueves_yElViernesSi() {
        Habito leer = diarioCreadoEl(VIERNES, "es");
        for (LocalDate dia = LUNES; dia.isBefore(VIERNES); dia = dia.plusDays(1)) {
            LocalDate fecha = dia;
            assertThrows(ConflictoException.class, () -> paso(leer, fecha), "antes del alta: " + fecha);
        }
        assertEquals(List.of(), registroDAO.findByHabito(em.find(Habito.class, leer.getHabitoId())));

        Map<String, Object> viernes = paso(leer, VIERNES);
        assertEquals(25, viernes.get("puntosPaso"));
        assertEquals(0, viernes.get("puntosHito"), "un solo día hecho: ningún hito");
    }

    @Test
    void elRechazoEsUn409Traducido() throws Exception {
        Habito leer = diarioCreadoEl(VIERNES, "en");
        String token = jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
        mockMvc.perform(post("/api/registros/completar/" + leer.getHabitoId())
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"fecha\":\"" + LUNES + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("before the habit was created")));
    }

    @Test
    void elDiaDelAltaYLosPosteriores_comoSiempre() {
        Habito leer = diarioCreadoEl(LUNES.plusDays(1), "es");
        assertThrows(ConflictoException.class, () -> paso(leer, LUNES));
        assertEquals(25, paso(leer, LUNES.plusDays(1)).get("puntosPaso"));
        assertEquals(25, paso(leer, LUNES.plusDays(2)).get("puntosPaso"));
    }
}
