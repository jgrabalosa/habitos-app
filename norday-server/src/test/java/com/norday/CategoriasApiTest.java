package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.habitos.model.Categoria;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Las categorías propias (T-031a) de punta a punta, contra PostgreSQL: el
 * contrato del GET (DTO sin creador), crear/editar con su validación (400) y
 * sus conflictos (409), el tope de 20, el borrado que deja los hábitos «sin
 * categoría» (la clave ajena FK_habito_categoria lo exige, y un unitario con
 * DAOs mockeados no la ve) y los 403 sobre categorías ajenas y globales.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CategoriasApiTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private IUsuarioLogroDAO usuarioLogroDAO;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private String token;

    @BeforeEach
    void unUsuario() {
        usuario = nuevoUsuario("cat");
        token = jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
    }

    private Usuario nuevoUsuario(String prefijo) {
        String sufijo = prefijo + "-" + System.nanoTime();
        Usuario u = new Usuario();
        u.setNombre("Test " + prefijo);
        u.setUsername("test-" + sufijo);
        u.setEmail("test-" + sufijo + "@example.com");
        u.setContrasena("contrasenaDePrueba");
        u.setProveedorAuth("LOCAL");
        u.setFechaRegistro(LocalDateTime.now());
        u.setIdioma("es");
        u.setZonaHoraria("Europe/Madrid");
        em.persist(u);
        em.flush();
        return u;
    }

    private MockHttpServletRequestBuilder conCuerpo(MockHttpServletRequestBuilder peticion, String cuerpo) {
        return peticion.header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo);
    }

    private static String cuerpo(String nombre) {
        return "{\"nombre\":\"" + nombre + "\"}";
    }

    /** Crea por la API y devuelve el id de la categoría. */
    private int crear(String nombre) throws Exception {
        MvcResult resultado = mockMvc.perform(conCuerpo(post("/api/categorias"), cuerpo(nombre)))
                .andExpect(status().isCreated())
                .andReturn();
        em.flush();
        String json = resultado.getResponse().getContentAsString();
        return Integer.parseInt(json.replaceAll(".*\"categoriaId\":(\\d+).*", "$1"));
    }

    private Categoria propia(Usuario creador, String nombre) {
        Categoria c = new Categoria(null, nombre, null, null, null, false, 0, creador);
        em.persist(c);
        em.flush();
        return c;
    }

    private int globalSalud() {
        return em.createQuery("SELECT c.categoriaId FROM Categoria c WHERE c.codigo = 'CAT_SALUD'", Integer.class)
                .getSingleResult();
    }

    // ── GET ──────────────────────────────────────────────────

    @Test
    void getDevuelveDtoSinCreador() throws Exception {
        int id = crear("Comer");
        mockMvc.perform(get("/api/categorias/usuario/" + usuario.getUsuarioId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                // la propia, con las claves de siempre
                .andExpect(jsonPath("$[?(@.categoriaId == " + id + ")].nombre").value(hasItem("Comer")))
                .andExpect(jsonPath("$[?(@.categoriaId == " + id + ")].codigo").value(hasItem(nullValue())))
                .andExpect(jsonPath("$[?(@.categoriaId == " + id + ")].esGlobal").value(hasItem(false)))
                // y una global trae código e icono
                .andExpect(jsonPath("$[?(@.codigo == 'CAT_SALUD')].icono").value(hasItem("❤️")))
                .andExpect(jsonPath("$[?(@.codigo == 'CAT_SALUD')].esGlobal").value(hasItem(true)));
    }

    @Test
    void getNoLlevaLaClaveCreadorEnNingunElemento() throws Exception {
        crear("Comer");
        String json = mockMvc.perform(get("/api/categorias/usuario/" + usuario.getUsuarioId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertFalse(json.contains("creador"), json);
        assertTrue(json.contains("\"categoriaId\""), json);
        assertTrue(json.contains("\"codigo\""), json);
        assertTrue(json.contains("\"icono\""), json);
    }

    // ── POST ─────────────────────────────────────────────────

    @Test
    void crearDevuelve201ConLaCategoriaYSuId() throws Exception {
        mockMvc.perform(conCuerpo(post("/api/categorias"),
                        "{\"nombre\":\"  Comer  \",\"icono\":\"🍎\",\"codigo\":\"CAT_SALUD\",\"esGlobal\":true,\"color\":\"#fff\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.categoriaId").isNumber())
                .andExpect(jsonPath("$.nombre").value("Comer"))
                .andExpect(jsonPath("$.icono").value("🍎"))
                .andExpect(jsonPath("$.codigo").value(nullValue()))
                .andExpect(jsonPath("$.esGlobal").value(false))
                .andExpect(jsonPath("$.color").value(nullValue()))
                .andExpect(jsonPath("$.creador").doesNotExist());
    }

    @Test
    void crearConNombreNoValido_es400() throws Exception {
        for (String nombre : List.of("", "   ", "a", " a ", "a".repeat(31))) {
            mockMvc.perform(conCuerpo(post("/api/categorias"), cuerpo(nombre)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").exists());
        }
        mockMvc.perform(conCuerpo(post("/api/categorias"), "{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crearConNombreDeLosLimites_vale() throws Exception {
        crear("ab");
        crear("a".repeat(30));
    }

    @Test
    void crearConIconoNoValido_es400() throws Exception {
        mockMvc.perform(conCuerpo(post("/api/categorias"), "{\"nombre\":\"Comer\",\"icono\":\"\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(conCuerpo(post("/api/categorias"),
                        "{\"nombre\":\"Comer\",\"icono\":\"" + "a".repeat(17) + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void nombreRepetidoSinMayusculasNiEspacios_es409() throws Exception {
        crear("Comer");
        mockMvc.perform(conCuerpo(post("/api/categorias"), cuerpo(" comer ")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void nombreDeUnaGlobal_es409() throws Exception {
        mockMvc.perform(conCuerpo(post("/api/categorias"), cuerpo("Salud")))
                .andExpect(status().isConflict());
        mockMvc.perform(conCuerpo(post("/api/categorias"), cuerpo("SALUD")))
                .andExpect(status().isConflict());
    }

    @Test
    void elMismoNombreQueUnaCategoriaDeOtroUsuario_vale() throws Exception {
        propia(nuevoUsuario("otro"), "Comer");
        crear("Comer");
    }

    @Test
    void laNumero21_es409() throws Exception {
        for (int i = 1; i <= 20; i++) {
            propia(usuario, "Categoría " + i);
        }
        mockMvc.perform(conCuerpo(post("/api/categorias"), cuerpo("Una más")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").exists());
    }

    @Test
    void laNumero20_vale() throws Exception {
        for (int i = 1; i <= 19; i++) {
            propia(usuario, "Categoría " + i);
        }
        crear("La veinte");
    }

    @Test
    void crearLaPrimeraDaElLogroPrimeraCategoria() throws Exception {
        assertFalse(tieneLogro("PRIMERA_CATEGORIA"));
        crear("Comer");
        assertTrue(tieneLogro("PRIMERA_CATEGORIA"));
    }

    private boolean tieneLogro(String codigo) {
        em.flush();
        List<UsuarioLogro> logros = usuarioLogroDAO.findByUsuario(em.find(Usuario.class, usuario.getUsuarioId()));
        return logros.stream().anyMatch(ul -> codigo.equals(ul.getLogro().getCodigo()));
    }

    // ── PUT ──────────────────────────────────────────────────

    @Test
    void editarCambiaNombreEIcono_yDevuelveElDto() throws Exception {
        int id = crear("Comer");
        mockMvc.perform(conCuerpo(put("/api/categorias/" + id),
                        "{\"nombre\":\" Cenar \",\"icono\":\"🌙\",\"color\":\"#000\",\"orden\":9}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.categoriaId").value(id))
                .andExpect(jsonPath("$.nombre").value("Cenar"))
                .andExpect(jsonPath("$.icono").value("🌙"))
                .andExpect(jsonPath("$.color").value(nullValue()))
                .andExpect(jsonPath("$.orden").value(0));
        em.flush();
        em.clear();
        assertEquals("Cenar", em.find(Categoria.class, id).getNombre());
    }

    @Test
    void editarANombreRepetido_es409() throws Exception {
        crear("Comer");
        int otra = crear("Cenar");
        mockMvc.perform(conCuerpo(put("/api/categorias/" + otra), cuerpo("COMER")))
                .andExpect(status().isConflict());
        mockMvc.perform(conCuerpo(put("/api/categorias/" + otra), cuerpo("Salud")))
                .andExpect(status().isConflict());
    }

    @Test
    void editarseASiMismaConElMismoNombre_es200() throws Exception {
        int id = crear("Comer");
        mockMvc.perform(conCuerpo(put("/api/categorias/" + id), cuerpo("comer")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("comer"));
    }

    @Test
    void editarConNombreNoValido_es400YNoCambiaNada() throws Exception {
        int id = crear("Comer");
        mockMvc.perform(conCuerpo(put("/api/categorias/" + id), cuerpo("a")))
                .andExpect(status().isBadRequest());
        em.flush();
        em.clear();
        assertEquals("Comer", em.find(Categoria.class, id).getNombre());
    }

    // ── DELETE ───────────────────────────────────────────────

    private int crearHabito(String nombre, int categoriaId) throws Exception {
        mockMvc.perform(conCuerpo(post("/api/habitos"),
                        "{\"nombre\":\"" + nombre + "\",\"descripcion\":\"D\",\"frecuencia\":\"DIARIO\",\"meta\":1,"
                                + "\"diasSemana\":null,\"recordatorioActivo\":true,\"recordatorioHora\":null,"
                                + "\"categoriaId\":" + categoriaId + "}"))
                .andExpect(status().isCreated());
        em.flush();
        return em.createQuery("SELECT h.habitoId FROM Habito h WHERE h.propietario.usuarioId = :u AND h.nombre = :n",
                        Integer.class)
                .setParameter("u", usuario.getUsuarioId())
                .setParameter("n", nombre)
                .getSingleResult();
    }

    @Test
    void borrarUnaCategoriaConDosHabitos_losDejaSinCategoriaYLaCategoriaDesaparece() throws Exception {
        int categoria = crear("Comer");
        int activo = crearHabito("Desayunar", categoria);
        int inactivo = crearHabito("Cenar", categoria);
        int deOtraCategoria = crearHabito("Correr", globalSalud());
        em.createQuery("UPDATE Habito h SET h.activo = false WHERE h.habitoId = :id")
                .setParameter("id", inactivo).executeUpdate();
        em.flush();
        em.clear();

        mockMvc.perform(delete("/api/categorias/" + categoria).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        em.flush();
        em.clear();

        assertNull(em.find(Categoria.class, categoria));
        Habito a = em.find(Habito.class, activo);
        Habito i = em.find(Habito.class, inactivo);
        assertNotNull(a);
        assertNotNull(i);
        assertNull(a.getTipo());
        assertNull(i.getTipo());
        // no cambia nada más
        assertEquals("Desayunar", a.getNombre());
        assertTrue(a.isActivo());
        assertFalse(i.isActivo());
        // los de otra categoría no se tocan
        assertEquals(globalSalud(), em.find(Habito.class, deOtraCategoria).getTipo().getCategoriaId());
    }

    @Test
    void borrarUnaCategoriaSinHabitos_laBorra() throws Exception {
        int categoria = crear("Comer");
        mockMvc.perform(delete("/api/categorias/" + categoria).header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        em.flush();
        em.clear();
        assertNull(em.find(Categoria.class, categoria));
    }

    // ── 403 ──────────────────────────────────────────────────

    @Test
    void editarOBorrarLaDeOtroUsuario_es403() throws Exception {
        Categoria ajena = propia(nuevoUsuario("ajeno"), "De otro");
        mockMvc.perform(conCuerpo(put("/api/categorias/" + ajena.getCategoriaId()), cuerpo("Mía")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/categorias/" + ajena.getCategoriaId()).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        em.flush();
        em.clear();
        assertEquals("De otro", em.find(Categoria.class, ajena.getCategoriaId()).getNombre());
    }

    @Test
    void editarOBorrarUnaGlobal_es403() throws Exception {
        int global = globalSalud();
        mockMvc.perform(conCuerpo(put("/api/categorias/" + global), cuerpo("Mía")))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/categorias/" + global).header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
        em.flush();
        em.clear();
        assertNotNull(em.find(Categoria.class, global));
    }
}
