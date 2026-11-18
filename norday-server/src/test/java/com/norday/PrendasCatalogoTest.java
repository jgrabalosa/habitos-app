package com.norday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.gamificacion.config.CatalogoGamificacionInitializer;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.dto.OfertaDTO;
import com.norday.gamificacion.repository.IOfertaDAO;
import com.norday.gamificacion.repository.IProductoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El alta de las prendas de Nori (Nori vestida, N-13): las siembra
 * CatalogoGamificacionInitializer al arrancar, con su colección, la versión
 * mínima del core y una oferta cada una.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class PrendasCatalogoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper json;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private IOfertaDAO ofertaDAO;
    @Autowired private CatalogoGamificacionInitializer inicializador;

    @PersistenceContext
    private EntityManager em;

    /** codigo → {sitio, coleccion, precio, hasta}. */
    private static final Map<String, Object[]> ESPERADAS = Map.of(
            "PRENDA_GORRO_LANA", new Object[]{"Cabeza", "INVIERNO_2026", 200, LocalDate.of(2027, 2, 28)},
            "PRENDA_BUFANDA", new Object[]{"Cuerpo", "INVIERNO_2026", 250, LocalDate.of(2027, 2, 28)},
            "PRENDA_GORRO_NOEL", new Object[]{"Cabeza", "NAVIDAD_2026", 0, LocalDate.of(2027, 1, 6)},
            "PRENDA_TRAJE_NOEL", new Object[]{"Cuerpo", "NAVIDAD_2026", 400, LocalDate.of(2027, 1, 6)});

    @Test
    void lasCuatro_conSuSitio_coleccion_versionYUnaOferta() {
        LocalDate hoyUtc = LocalDate.now(ZoneOffset.UTC);
        ESPERADAS.forEach((codigo, e) -> {
            Producto p = productoDAO.findByCodigo(codigo);
            assertNotNull(p, codigo);
            assertTrue(p.isActivo(), codigo);
            assertNull(p.getOrigenApp(), codigo + " es de todas las apps");
            assertEquals("EQUIPABLE", p.getTipo(), codigo);
            assertEquals(e[0], p.getCategoria(), codigo);
            assertEquals(e[1], p.getColeccion(), codigo);
            assertEquals("0.56.0", p.getVersionMinimaCore(), codigo);

            List<OfertaDTO> ofertas = ofertaDAO.deProducto(p.getProductoId());
            assertEquals(1, ofertas.size(), codigo);
            OfertaDTO o = ofertas.get(0);
            assertEquals(e[2], o.precio(), codigo);
            assertEquals(e[3], o.hasta(), codigo);
            assertTrue(o.incluye(hoyUtc), codigo + " ya se vende hoy");
            assertTrue(o.incluye(hoyUtc.minusDays(1)), codigo + " ha empezado también en las zonas por detrás de UTC");
        });
    }

    @Test
    void volverAArrancar_noDuplicaNiProductosNiOfertas_yLasDejaActivas() throws Exception {
        inicializador.run();
        inicializador.run();
        em.flush();
        for (String codigo : ESPERADAS.keySet()) {
            Long productos = em.createQuery("SELECT COUNT(p) FROM Producto p WHERE p.codigo = :c", Long.class)
                    .setParameter("c", codigo).getSingleResult();
            assertEquals(1L, productos, codigo);
            Producto p = productoDAO.findByCodigo(codigo);
            assertTrue(p.isActivo(), codigo + " no se retira: está en el catálogo del motor");
            assertEquals(1, ofertaDAO.deProducto(p.getProductoId()).size(), codigo);
        }
    }

    @Test
    void unaPrendaQueYaExistiaSinOferta_recibeLaSuya() throws Exception {
        Producto lana = productoDAO.findByCodigo("PRENDA_GORRO_LANA");
        em.createQuery("DELETE FROM Oferta o WHERE o.producto.productoId = :id")
                .setParameter("id", lana.getProductoId()).executeUpdate();
        assertTrue(ofertaDAO.deProducto(lana.getProductoId()).isEmpty());

        inicializador.run();
        em.flush();

        List<OfertaDTO> ofertas = ofertaDAO.deProducto(lana.getProductoId());
        assertEquals(1, ofertas.size());
        assertEquals(200, ofertas.get(0).precio());
    }

    @Test
    void conElCoreNuevo_salenEnElCatalogoConSuColeccionYOferta_ySinCabeceraNo() throws Exception {
        Usuario usuario = usuario();
        String token = "Bearer " + jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());

        JsonNode nuevo = json.readTree(mockMvc.perform(get("/api/gamificacion/productos/catalogo")
                        .header("Authorization", token).header("X-Norday-App", "habitos")
                        .header("X-Norday-Core", "0.56.0"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        for (String codigo : ESPERADAS.keySet()) {
            JsonNode p = StreamSupport.stream(nuevo.spliterator(), false)
                    .filter(n -> codigo.equals(n.get("codigo").asText())).findFirst().orElseThrow();
            assertEquals(ESPERADAS.get(codigo)[1], p.get("coleccion").asText(), codigo);
            assertEquals(1, p.get("ofertas").size(), codigo);
            assertEquals(((LocalDate) ESPERADAS.get(codigo)[3]).toString(),
                    p.get("ofertas").get(0).get("hasta").asText(), codigo);
        }

        String viejo = mockMvc.perform(get("/api/gamificacion/productos/catalogo")
                        .header("Authorization", token).header("X-Norday-App", "habitos"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertFalse(viejo.contains("PRENDA_"), "una APK con el core 0.55.0 no las ve");
    }

    @Test
    void elGorroDePapaNoel_esGratis() throws Exception {
        Usuario usuario = usuario();
        String token = "Bearer " + jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
        Producto noel = productoDAO.findByCodigo("PRENDA_GORRO_NOEL");

        mockMvc.perform(post("/api/gamificacion/productos/comprar/" + usuario.getUsuarioId() + "/" + noel.getProductoId())
                        .header("Authorization", token).header("X-Norday-Core", "0.56.0"))
                .andExpect(status().isOk());

        Long suyas = em.createQuery("SELECT COUNT(up) FROM UsuarioProducto up "
                        + "WHERE up.usuario.usuarioId = :u AND up.producto.productoId = :p", Long.class)
                .setParameter("u", usuario.getUsuarioId()).setParameter("p", noel.getProductoId())
                .getSingleResult();
        assertEquals(1L, suyas, "sin saldo, lo tiene");
    }

    private Usuario usuario() {
        Usuario u = new Usuario();
        u.setNombre("Test Prendas");
        u.setUsername("test-prendas-" + System.nanoTime());
        u.setEmail("test-prendas-" + System.nanoTime() + "@example.com");
        u.setContrasena("contrasenaDePrueba");
        u.setProveedorAuth("LOCAL");
        u.setFechaRegistro(LocalDateTime.now());
        u.setIdioma("es");
        u.setZonaHoraria("Europe/Madrid");
        em.persist(u);
        em.flush();
        return u;
    }
}
