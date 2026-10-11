package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * La respuesta de Hoy de punta a punta: token, controlador, servicio, base
 * de datos y JSON. Comprueba la forma de la respuesta —los nombres de los
 * campos son el contrato con la app—, no las reglas, que ya tienen sus tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HoyEndpointTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @PersistenceContext
    private EntityManager em;

    private final LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private int usuarioId;
    private int habitoId;
    private String token;

    /** Un usuario con un diario creado hace un mes y hecho hoy. */
    @BeforeEach
    void unUsuarioConUnDiarioHechoHoy() {
        String email = "test-hoy-" + System.nanoTime() + "@example.com";
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Hoy");
        usuario.setUsername("test-hoy-" + System.nanoTime());
        usuario.setEmail(email);
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito leer = new Habito("Leer", "Descripción", Frecuencia.DIARIO, 1,
                usuario, null, hoy.minusDays(30));
        em.persist(leer);
        em.persist(new Ritmo(leer, hoy));
        em.persist(new Registro(leer, true, "", hoy));
        em.flush();
        em.clear();

        usuarioId = usuario.getUsuarioId();
        habitoId = leer.getHabitoId();
        token = jwtUtil.generateToken(usuarioId, email);
    }

    @Test
    void devuelveLasDosSeccionesLaTiraYElRitmo() throws Exception {
        mockMvc.perform(get("/api/habitos/usuario/" + usuarioId + "/hoy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fecha").value(hoy.toString()))
                .andExpect(jsonPath("$.hoy").value(hoy.toString()))
                .andExpect(jsonPath("$.dias.length()").value(7))
                .andExpect(jsonPath("$.avance").value(1.0))
                .andExpect(jsonPath("$.ritmoNivel").value(2))
                .andExpect(jsonPath("$.ritmoTendencia").value("IGUAL"))
                .andExpect(jsonPath("$.delDia.hechos").value(1))
                .andExpect(jsonPath("$.delDia.total").value(1))
                .andExpect(jsonPath("$.delDia.habitos[0].habito.nombre").value("Leer"))
                .andExpect(jsonPath("$.delDia.habitos[0].completados").value(1))
                .andExpect(jsonPath("$.delDia.habitos[0].meta").value(1))
                .andExpect(jsonPath("$.delDia.habitos[0].hecho").value(true))
                .andExpect(jsonPath("$.delDia.habitos[0].completadoEnFecha").value(true))
                .andExpect(jsonPath("$.delDia.habitos[0].ritmoNivel").value(2))
                .andExpect(jsonPath("$.delDia.habitos[0].fechasCompletadas[0]").value(hoy.toString()))
                .andExpect(jsonPath("$.deLaSemana.hechos").value(0))
                .andExpect(jsonPath("$.deLaSemana.total").value(0))
                .andExpect(jsonPath("$.logrosOtorgados").isArray())
                .andExpect(jsonPath("$.logrosOtorgados.length()").value(0));
    }

    @Test
    void conUnaFechaMalEscrita_devuelve400() throws Exception {
        mockMvc.perform(get("/api/habitos/usuario/" + usuarioId + "/hoy")
                        .param("fecha", "ayer")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest());
    }

    @Test
    void elDetalleLlevaElRitmoYSigueLlevandoLaRacha() throws Exception {
        mockMvc.perform(get("/api/habitos/" + habitoId + "/detalle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Leer"))
                .andExpect(jsonPath("$.ritmoNivel").value(2))
                .andExpect(jsonPath("$.ritmoTendencia").value("IGUAL"))
                .andExpect(jsonPath("$.totalCompletados").value(1))
                .andExpect(jsonPath("$.rachaActual").value(0))
                .andExpect(jsonPath("$.rachaMaxima").value(0));
    }

    @Test
    void conUnHabitoEnImparable_laPrimeraLecturaConcedeLosLogrosDeRitmoYLaSegundaNo() throws Exception {
        // El ancla del hábito pasa a 0,97: nivel 5 ("Imparable"). Como es el
        // único hábito y tiene un mes, el ritmo general también llega.
        em.createQuery("UPDATE Ritmo r SET r.valor = 0.97 WHERE r.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
        em.clear();

        mockMvc.perform(get("/api/habitos/usuario/" + usuarioId + "/hoy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ritmoNivel").value(5))
                .andExpect(jsonPath("$.logrosOtorgados", hasItems(
                        "RITMO_BUENO", "RITMO_FUERTE", "RITMO_IMPARABLE", "RITMO_GENERAL_FUERTE")));

        // Ya los tiene: una segunda lectura no vuelve a concederlos.
        mockMvc.perform(get("/api/habitos/usuario/" + usuarioId + "/hoy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.logrosOtorgados.length()").value(0));
    }

    @Test
    void laMascotaLlevaElAnimoNuevoYSigueLlevandoElEstadoDeSiempre() throws Exception {
        // El único hábito es un diario y está hecho hoy: el día está completo.
        mockMvc.perform(get("/api/mascota/" + usuarioId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("feliz"))
                .andExpect(jsonPath("$.animo").value("exultante"));
    }
}
