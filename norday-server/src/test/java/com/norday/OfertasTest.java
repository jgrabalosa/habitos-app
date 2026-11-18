package com.norday;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.gamificacion.model.Oferta;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.service.RelojTienda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.StreamSupport;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las ofertas por periodo contra la aplicación ensamblada (Nori vestida,
 * N-4): el catálogo se pide por HTTP y se lee entero (sin carga perezosa al
 * pasar a JSON), con los campos de siempre más «coleccion» y «ofertas»; y el
 * inventario y la exportación siguen con su forma.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class OfertasTest {

    /** Los campos que el catálogo de productos devolvía antes de T-022. */
    private static final Set<String> CAMPOS_DE_ANTES = Set.of(
            "productoId", "nombre", "descripcion", "codigo", "categoria", "tipo",
            "precio", "icono", "activo", "origenApp");

    private static final LocalDate D1 = LocalDate.of(2026, 12, 1);
    private static final LocalDate D6E = LocalDate.of(2027, 1, 6);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private ObjectMapper json;

    @MockitoSpyBean
    private RelojTienda reloj;

    @PersistenceContext
    private EntityManager em;

    private Producto gorro;

    @BeforeEach
    void datos() {
        gorro = new Producto("TEST_GORRO_" + System.nanoTime(), "Gorro de prueba", "Para el test",
                "Cabeza", "EQUIPABLE", 250, null);
        gorro.setColeccion("NAVIDAD_2026");
        em.persist(gorro);
        // La segunda primero: el catálogo las ordena por desde.
        em.persist(new Oferta(gorro, D1.plusDays(7), D6E, 200));
        em.persist(new Oferta(gorro, D1, D1.plusDays(6), 0));
        em.flush();
        em.clear();
    }

    private void hoyUtc(String instante) {
        doReturn(Instant.parse(instante)).when(reloj).ahora();
    }

    private JsonNode catalogo() throws Exception {
        // El catálogo pide sesión; el token vale aunque el usuario no exista.
        String token = "Bearer " + jwtUtil.generateToken(1, "titular@norday.local");
        String cuerpo = mockMvc.perform(get("/api/gamificacion/productos/catalogo")
                        .header("Authorization", token)
                        .header("X-Norday-App", "habitos"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private JsonNode delCatalogo(JsonNode catalogo, String codigo) {
        return StreamSupport.stream(catalogo.spliterator(), false)
                .filter(p -> codigo.equals(p.get("codigo").asText()))
                .findFirst().orElse(null);
    }

    private static Set<String> campos(JsonNode nodo) {
        Set<String> campos = new HashSet<>();
        nodo.fieldNames().forEachRemaining(campos::add);
        return campos;
    }

    @Test
    void elCatalogo_porHttp_traeLasOfertasYLaColeccion_yLosCamposDeSiempre() throws Exception {
        hoyUtc("2026-11-20T12:00:00Z");

        JsonNode catalogo = catalogo();
        JsonNode prenda = delCatalogo(catalogo, gorro.getCodigo());

        assertNotNull(prenda, "una oferta futura sale en el catálogo");
        Set<String> esperados = new HashSet<>(CAMPOS_DE_ANTES);
        esperados.add("coleccion");
        esperados.add("ofertas");
        assertEquals(esperados, campos(prenda));
        assertEquals("NAVIDAD_2026", prenda.get("coleccion").asText());
        JsonNode ofertas = prenda.get("ofertas");
        assertEquals(2, ofertas.size());
        assertEquals("2026-12-01", ofertas.get(0).get("desde").asText());
        assertEquals("2026-12-07", ofertas.get(0).get("hasta").asText());
        assertEquals(0, ofertas.get(0).get("precio").asInt());
        assertEquals("2026-12-08", ofertas.get(1).get("desde").asText());
        assertEquals(200, ofertas.get(1).get("precio").asInt());
        assertEquals(Set.of("desde", "hasta", "precio"), campos(ofertas.get(0)));

        // Lo de siempre sigue igual: sin ofertas, la lista vacía.
        JsonNode tema = delCatalogo(catalogo, "TEMA_PROFUNDIDAD");
        assertNotNull(tema);
        assertTrue(tema.get("ofertas").isArray());
        assertEquals(0, tema.get("ofertas").size());
        assertTrue(tema.get("coleccion").isNull());
    }

    @Test
    void conTodasSusOfertasTerminadas_noSaleEnElCatalogo() throws Exception {
        hoyUtc("2027-01-07T00:00:00Z");

        assertNull(delCatalogo(catalogo(), gorro.getCodigo()));
    }

    @Test
    void elInventario_siguenSinOfertas_yLaExportacionNoCambiaDeForma() throws Exception {
        hoyUtc("2026-12-02T12:00:00Z");
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Ofertas");
        usuario.setUsername("test-ofertas-" + System.nanoTime());
        usuario.setEmail("test-ofertas-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        em.persist(new UsuarioProducto(usuario, em.find(Producto.class, gorro.getProductoId()), 1));
        em.flush();
        em.clear();
        String token = "Bearer " + jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());

        JsonNode inventario = json.readTree(mockMvc.perform(
                        get("/api/gamificacion/productos/usuario/" + usuario.getUsuarioId()).header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode producto = inventario.get(0).get("producto");
        Set<String> esperados = new HashSet<>(CAMPOS_DE_ANTES);
        esperados.add("coleccion");
        assertEquals(esperados, campos(producto), "el inventario no trae ofertas");

        JsonNode exportado = json.readTree(mockMvc.perform(
                        get("/api/usuarios/" + usuario.getUsuarioId() + "/exportar").header("Authorization", token))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        JsonNode productos = exportado.findValue("productos");
        assertNotNull(productos);
        assertEquals(Set.of("codigo", "nombre", "cantidad", "equipado", "fechaAdquirido"), campos(productos.get(0)));
    }

    @Test
    void laCompraPorHttp_fueraDeTemporada_da409_yDentro_cobraLaOferta() throws Exception {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Ofertas Compra");
        usuario.setUsername("test-ofertas-c-" + System.nanoTime());
        usuario.setEmail("test-ofertas-c-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        em.flush();
        String token = "Bearer " + jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
        String url = "/api/gamificacion/productos/comprar/" + usuario.getUsuarioId() + "/" + gorro.getProductoId();

        hoyUtc("2026-11-29T12:00:00Z");
        mockMvc.perform(post(url)
                        .header("Authorization", token))
                .andExpect(status().isConflict());

        hoyUtc("2026-12-01T12:00:00Z");
        mockMvc.perform(post(url)
                        .header("Authorization", token))
                .andExpect(status().isOk());

        em.flush();
        em.clear();
        List<Object[]> movimientos = em.createQuery(
                        "SELECT m.cantidad, m.origen FROM UsuarioMoneda m WHERE m.usuario.usuarioId = :u", Object[].class)
                .setParameter("u", usuario.getUsuarioId())
                .getResultList();
        assertEquals(1, movimientos.size());
        assertEquals(0, ((Number) movimientos.get(0)[0]).intValue());
        assertEquals("REGALO", movimientos.get(0)[1]);
    }
}
