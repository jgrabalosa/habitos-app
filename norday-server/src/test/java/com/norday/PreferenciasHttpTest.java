package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * GET y PUT /api/usuarios/{id}/preferencias contra la cadena de verdad. Lo
 * que importa: el cuerpo que manda la APK ya instalada (solo textos) sigue
 * valiendo, y avisosApp se lee y se cambia, con un campo ausente o null como
 * «sin cambio».
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PreferenciasHttpTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private String ruta;
    private String token;

    @BeforeEach
    void prepararUsuario() {
        usuario = new Usuario();
        usuario.setNombre("Test Preferencias");
        usuario.setUsername("test-pref-" + System.nanoTime());
        usuario.setEmail("test-pref-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        em.persist(usuario);
        em.flush();
        ruta = "/api/usuarios/" + usuario.getUsuarioId() + "/preferencias";
        token = jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
    }

    private MockHttpServletRequestBuilder conCuerpo(String json) {
        return put(ruta)
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json);
    }

    @Test
    void get_devuelveAvisosAppEncendidoPorDefecto() throws Exception {
        mockMvc.perform(get(ruta).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("es"))
                .andExpect(jsonPath("$.zonaHoraria").value("Europe/Madrid"))
                .andExpect(jsonPath("$.avisosApp").value(true));
    }

    @Test
    void put_conElCuerpoDeLaAppInstalada_soloTextos_siguePasando() throws Exception {
        mockMvc.perform(conCuerpo("{\"idioma\":\"en\",\"zonaHoraria\":\"Europe/London\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.idioma").value("en"))
                .andExpect(jsonPath("$.zonaHoraria").value("Europe/London"))
                .andExpect(jsonPath("$.avisosApp").value(true));
    }

    @Test
    void put_conAvisosAppBooleano_loApagaYLoEnciende() throws Exception {
        mockMvc.perform(conCuerpo("{\"avisosApp\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosApp").value(false))
                .andExpect(jsonPath("$.idioma").value("es"));
        mockMvc.perform(get(ruta).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.avisosApp").value(false));

        mockMvc.perform(conCuerpo("{\"avisosApp\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosApp").value(true));
    }

    @Test
    void put_sinAvisosAppONull_noLoCambia() throws Exception {
        mockMvc.perform(conCuerpo("{\"avisosApp\":false}")).andExpect(status().isOk());

        mockMvc.perform(conCuerpo("{\"idioma\":\"pt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosApp").value(false));
        mockMvc.perform(conCuerpo("{\"avisosApp\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosApp").value(false));
    }

    @Test
    void put_aceptaTrueFalseComoTexto() throws Exception {
        mockMvc.perform(conCuerpo("{\"avisosApp\":\"false\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avisosApp").value(false));
    }

    @Test
    void put_conUnValorQueNoEsBooleano_da400YNoCambiaNada() throws Exception {
        mockMvc.perform(conCuerpo("{\"avisosApp\":\"quizá\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conCuerpo("{\"avisosApp\":0}"))
                .andExpect(status().isBadRequest());

        mockMvc.perform(get(ruta).header("Authorization", "Bearer " + token))
                .andExpect(jsonPath("$.avisosApp").value(true));
    }

    @Test
    void put_conIdiomaQueNoEsTexto_da400() throws Exception {
        mockMvc.perform(conCuerpo("{\"idioma\":5}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void laColumnaGuardaElValor() throws Exception {
        mockMvc.perform(conCuerpo("{\"avisosApp\":false}")).andExpect(status().isOk());
        em.flush();

        Object valor = em.createNativeQuery(
                        "SELECT avisos_app FROM usuario WHERE usuario_id = :id")
                .setParameter("id", usuario.getUsuarioId())
                .getSingleResult();

        assertEquals(Boolean.FALSE, valor);
    }
}
