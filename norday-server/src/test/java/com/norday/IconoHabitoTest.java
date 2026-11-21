package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.habitos.model.Habito;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El icono del hábito (T-011a), de punta a punta: se guarda al crear, se lee
 * de vuelta, se valida (400), un PUT sin icono conserva el que había (la app
 * ya instalada no lo manda) y /hoy lo trae en su fila.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class IconoHabitoTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @PersistenceContext
    private EntityManager em;

    private int usuarioId;
    private String token;

    @BeforeEach
    void unUsuario() {
        String email = "test-icono-" + System.nanoTime() + "@example.com";
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Icono");
        usuario.setUsername("test-icono-" + System.nanoTime());
        usuario.setEmail(email);
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        em.flush();
        usuarioId = usuario.getUsuarioId();
        token = jwtUtil.generateToken(usuarioId, email);
    }

    private static String cuerpo(String iconoJson) {
        String icono = iconoJson == null ? "" : ",\"icono\":" + iconoJson;
        return "{\"nombre\":\"Leer\",\"descripcion\":\"D\",\"frecuencia\":\"DIARIO\",\"meta\":1,"
                + "\"diasSemana\":null,\"recordatorioActivo\":true,\"recordatorioHora\":null" + icono + "}";
    }

    private MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    /** Crea el hábito por la API y devuelve su id. */
    private int crear(String iconoJson) throws Exception {
        mockMvc.perform(conCuerpo(post("/api/habitos"), cuerpo(iconoJson)))
                .andExpect(status().isCreated());
        em.flush();
        em.clear();
        return em.createQuery("SELECT h FROM Habito h WHERE h.propietario.usuarioId = :u", Habito.class)
                .setParameter("u", usuarioId)
                .getSingleResult()
                .getHabitoId();
    }

    private void leerIcono(int habitoId, String esperado) throws Exception {
        em.flush();
        em.clear();
        var lectura = mockMvc.perform(get("/api/habitos/" + habitoId)
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        if (esperado == null) {
            lectura.andExpect(jsonPath("$.icono").value(org.hamcrest.Matchers.nullValue()));
        } else {
            lectura.andExpect(jsonPath("$.icono").value(esperado));
        }
    }

    @Test
    void crearConIcono_loGuardaYSeLeeDeVuelta() throws Exception {
        int id = crear("\"libro1\"");
        leerIcono(id, "libro1");
    }

    @Test
    void crearSinIcono_quedaNulo() throws Exception {
        int id = crear(null);
        leerIcono(id, null);
    }

    @Test
    void crearConIconoNulo_quedaNulo() throws Exception {
        int id = crear("null");
        leerIcono(id, null);
    }

    @Test
    void iconoConEspacio_es400() throws Exception {
        mockMvc.perform(conCuerpo(post("/api/habitos"), cuerpo("\"a b\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void iconoDe41Caracteres_es400() throws Exception {
        mockMvc.perform(conCuerpo(post("/api/habitos"), cuerpo("\"" + "a".repeat(41) + "\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void iconoVacio_es400() throws Exception {
        mockMvc.perform(conCuerpo(post("/api/habitos"), cuerpo("\"\"")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void iconoDe40Caracteres_vale() throws Exception {
        String cuarenta = "a".repeat(40);
        int id = crear("\"" + cuarenta + "\"");
        leerIcono(id, cuarenta);
    }

    @Test
    void actualizarConIconoNoValido_es400YNoCambiaNada() throws Exception {
        int id = crear("\"libro1\"");
        mockMvc.perform(conCuerpo(put("/api/habitos/" + id), cuerpo("\"a b\"")))
                .andExpect(status().isBadRequest());
        leerIcono(id, "libro1");
    }

    @Test
    void actualizarSinIcono_conservaElAnterior() throws Exception {
        int id = crear("\"libro1\"");
        mockMvc.perform(conCuerpo(put("/api/habitos/" + id), cuerpo(null)))
                .andExpect(status().isOk());
        leerIcono(id, "libro1");
    }

    @Test
    void actualizarConIconoNulo_conservaElAnterior() throws Exception {
        int id = crear("\"libro1\"");
        mockMvc.perform(conCuerpo(put("/api/habitos/" + id), cuerpo("null")))
                .andExpect(status().isOk());
        leerIcono(id, "libro1");
    }

    @Test
    void actualizarConOtroIcono_loCambia() throws Exception {
        int id = crear("\"libro1\"");
        mockMvc.perform(conCuerpo(put("/api/habitos/" + id), cuerpo("\"pesa2\"")))
                .andExpect(status().isOk());
        leerIcono(id, "pesa2");
    }

    @Test
    void cambiarElIcono_noCreaVersionDelObjetivo() throws Exception {
        int id = crear("\"libro1\"");
        em.flush();
        long antes = (long) em.createQuery("SELECT COUNT(o) FROM HabitoObjetivo o").getSingleResult();
        mockMvc.perform(conCuerpo(put("/api/habitos/" + id), cuerpo("\"pesa2\"")))
                .andExpect(status().isOk());
        em.flush();
        long despues = (long) em.createQuery("SELECT COUNT(o) FROM HabitoObjetivo o").getSingleResult();
        assertEquals(antes, despues);
    }

    @Test
    void hoyTraeElIconoEnSuFila() throws Exception {
        crear("\"libro1\"");
        mockMvc.perform(get("/api/habitos/usuario/" + usuarioId + "/hoy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.delDia.habitos[0].habito.nombre").value("Leer"))
                .andExpect(jsonPath("$.delDia.habitos[0].habito.icono").value("libro1"));
    }

    @Test
    void detalleTraeElIcono() throws Exception {
        int id = crear("\"libro1\"");
        mockMvc.perform(get("/api/habitos/" + id + "/detalle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.icono").value("libro1"));
    }
}
