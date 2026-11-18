package com.norday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.gamificacion.model.Producto;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El catálogo y la compra según la versión del core, por HTTP (Nori vestida,
 * N-6). Las apps instaladas hoy (core 0.55.0) no mandan X-Norday-Core: no
 * ven ningún producto con versión mínima, ni Habits ni Focus.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CatalogoVersionCoreTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper json;

    @PersistenceContext
    private EntityManager em;

    private Producto prenda;
    private Usuario usuario;

    @BeforeEach
    void datos() {
        prenda = new Producto("TEST_VC_" + System.nanoTime(), "Prenda", "Para el test", "Cabeza", "EQUIPABLE", 0, null);
        prenda.setVersionMinimaCore("0.56.0");
        em.persist(prenda);
        usuario = new Usuario();
        usuario.setNombre("Test Versión Core");
        usuario.setUsername("test-vc-" + System.nanoTime());
        usuario.setEmail("test-vc-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        em.flush();
    }

    private String token() {
        return "Bearer " + jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
    }

    private boolean saleEnElCatalogo(String app, String version) throws Exception {
        MockHttpServletRequestBuilder peticion = get("/api/gamificacion/productos/catalogo")
                .header("Authorization", token());
        if (app != null) peticion = peticion.header("X-Norday-App", app);
        if (version != null) peticion = peticion.header("X-Norday-Core", version);
        JsonNode catalogo = json.readTree(mockMvc.perform(peticion).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());
        assertTrue(catalogo.size() > 0, "el resto del catálogo sigue saliendo");
        return StreamSupport.stream(catalogo.spliterator(), false)
                .anyMatch(p -> prenda.getCodigo().equals(p.get("codigo").asText()));
    }

    @Test
    void lasAppsDeHoy_sinCabecera_noLoVen_niHabitsNiFocus() throws Exception {
        assertFalse(saleEnElCatalogo(null, null));
        assertFalse(saleEnElCatalogo("habitos", null));
        assertFalse(saleEnElCatalogo("enfoque", null));
    }

    @Test
    void conUnCoreMenor_oIlegible_noLoVe() throws Exception {
        assertFalse(saleEnElCatalogo("habitos", "0.55.0"));
        assertFalse(saleEnElCatalogo("habitos", "0.56.0-beta"));
    }

    @Test
    void conElCoreNuevo_loVe() throws Exception {
        assertTrue(saleEnElCatalogo("habitos", "0.56.0"));
        assertTrue(saleEnElCatalogo("enfoque", "0.56.0"));
    }

    @Test
    void laVersionMinimaNoViajaAlCliente() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/gamificacion/productos/catalogo")
                        .header("Authorization", token()).header("X-Norday-Core", "0.56.0"))
                .andReturn().getResponse().getContentAsString();
        assertFalse(cuerpo.contains("versionMinimaCore"));
        assertFalse(cuerpo.contains("version_minima_core"));
    }

    @Test
    void comprarSinCabecera_da409_yConElCoreNuevo_seCompra() throws Exception {
        String url = "/api/gamificacion/productos/comprar/" + usuario.getUsuarioId() + "/" + prenda.getProductoId();

        String cuerpo = mockMvc.perform(post(url).header("Authorization", token()))
                .andExpect(status().isConflict())
                .andReturn().getResponse().getContentAsString();
        assertTrue(cuerpo.contains("Actualiza la app"), cuerpo);
        mockMvc.perform(post(url).header("Authorization", token()).header("X-Norday-Core", "0.55.0"))
                .andExpect(status().isConflict());

        mockMvc.perform(post(url).header("Authorization", token()).header("X-Norday-Core", "0.56.0"))
                .andExpect(status().isOk());
    }
}
