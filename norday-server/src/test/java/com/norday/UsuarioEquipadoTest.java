package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import com.norday.gamificacion.service.ProductoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.PersistenceException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Una cosa puesta por sitio, garantizado por la base de datos (Nori vestida,
 * N-5): la tabla usuario_equipado, contra Postgres de verdad. El
 * @Transactional deshace todo al terminar.
 */
@SpringBootTest
@Transactional
class UsuarioEquipadoTest {

    @Autowired private ProductoService productoService;
    @Autowired private IUsuarioProductoDAO usuarioProductoDAO;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private UsuarioService usuarioService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;

    @BeforeEach
    void usuario() {
        usuario = nuevoUsuario();
    }

    private Usuario nuevoUsuario() {
        Usuario u = new Usuario();
        u.setNombre("Test Equipado");
        u.setUsername("test-equipado-" + System.nanoTime());
        u.setEmail("test-equipado-" + System.nanoTime() + "@example.com");
        u.setContrasena("contrasenaDePrueba");
        u.setProveedorAuth("LOCAL");
        u.setFechaRegistro(LocalDateTime.now());
        u.setIdioma("es");
        u.setZonaHoraria("Europe/Madrid");
        em.persist(u);
        return u;
    }

    private Producto producto(String categoria) {
        Producto p = new Producto("TEST_EQ_" + System.nanoTime(), "Prueba", "Para el test",
                categoria, "EQUIPABLE", 100, null);
        em.persist(p);
        return p;
    }

    private UsuarioProducto tiene(Usuario u, Producto p, boolean equipado) {
        UsuarioProducto up = new UsuarioProducto(u, p, 1);
        up.setEquipado(equipado);
        em.persist(up);
        return up;
    }

    /** Las filas de usuario_equipado del usuario en ese sitio: id de lo puesto. */
    @SuppressWarnings("unchecked")
    private List<Integer> filas(Usuario u, String categoria) {
        em.flush();
        return ((List<Number>) em.createNativeQuery(
                        "SELECT usuario_producto_id FROM usuario_equipado WHERE usuario_id = ?1 AND categoria = ?2")
                .setParameter(1, u.getUsuarioId())
                .setParameter(2, categoria)
                .getResultList()).stream().map(Number::intValue).toList();
    }

    private boolean equipado(UsuarioProducto up) {
        em.flush();
        em.clear();
        return em.find(UsuarioProducto.class, up.getUsuarioProductoId()).isEquipado();
    }

    private void cambiarDePrenda(String categoria) {
        Producto a = producto(categoria);
        Producto b = producto(categoria);
        UsuarioProducto tieneA = tiene(usuario, a, false);
        UsuarioProducto tieneB = tiene(usuario, b, false);
        em.flush();

        productoService.equiparProducto(usuario, a.getProductoId());
        productoService.equiparProducto(usuario, b.getProductoId());

        assertEquals(List.of(tieneB.getUsuarioProductoId()), filas(usuario, categoria));
        assertTrue(equipado(tieneB));
        assertFalse(equipado(tieneA));
        assertEquals(tieneB.getUsuarioProductoId(),
                usuarioProductoDAO.findEquipadoPorCategoria(usuario.getUsuarioId(), categoria).getUsuarioProductoId());
    }

    @Test
    void cambiarDePrendaEnElMismoSitio_quedaLaNueva_enUnaSolaFila_tema() {
        cambiarDePrenda("Tema");
    }

    @Test
    void cambiarDePrendaEnElMismoSitio_quedaLaNueva_enUnaSolaFila_cabeza() {
        cambiarDePrenda("Cabeza");
    }

    @Test
    void dosPuestasEnElMismoSitio_lasRechazaLaBaseDeDatos_noLaLogica() {
        Producto a = producto("Cabeza");
        Producto b = producto("Cabeza");
        UsuarioProducto tieneA = tiene(usuario, a, true);
        UsuarioProducto tieneB = tiene(usuario, b, true);
        em.flush();
        String insertar = "INSERT INTO usuario_equipado (usuario_id, categoria, usuario_producto_id) VALUES (?1, 'Cabeza', ?2)";
        em.createNativeQuery(insertar)
                .setParameter(1, usuario.getUsuarioId()).setParameter(2, tieneA.getUsuarioProductoId())
                .executeUpdate();

        PersistenceException e = assertThrows(PersistenceException.class, () -> em.createNativeQuery(insertar)
                .setParameter(1, usuario.getUsuarioId()).setParameter(2, tieneB.getUsuarioProductoId())
                .executeUpdate());
        assertTrue(String.valueOf(e.getCause()).toLowerCase().contains("constraint")
                        || String.valueOf(e.getMessage()).toLowerCase().contains("duplicate"),
                "falla por la restricción: " + e);
    }

    @Test
    void equiparYDesequipar_comoHoy() {
        Producto a = producto("Cabeza");
        UsuarioProducto tieneA = tiene(usuario, a, false);
        em.flush();

        productoService.equiparProducto(usuario, a.getProductoId());
        assertEquals(List.of(tieneA.getUsuarioProductoId()), filas(usuario, "Cabeza"));
        assertTrue(equipado(tieneA));

        productoService.desequiparProducto(usuario, a.getProductoId());
        assertEquals(List.of(), filas(usuario, "Cabeza"));
        assertFalse(equipado(tieneA));
        assertNull(usuarioProductoDAO.findEquipadoPorCategoria(usuario.getUsuarioId(), "Cabeza"));
    }

    @Test
    void desequiparLoQueNoEstaPuesto_noVaciaElSitio() {
        Producto a = producto("Cabeza");
        Producto b = producto("Cabeza");
        UsuarioProducto tieneA = tiene(usuario, a, false);
        tiene(usuario, b, false);
        em.flush();
        productoService.equiparProducto(usuario, a.getProductoId());

        productoService.desequiparProducto(usuario, b.getProductoId());

        assertEquals(List.of(tieneA.getUsuarioProductoId()), filas(usuario, "Cabeza"));
    }

    @Test
    void laEleccionDeIdentidadDelOnboarding_dejaSuFila() {
        Producto tema = productoDAO.findByCodigo("TEMA_DULCE");
        em.flush();

        productoService.otorgarIdentidadElegida(usuario, tema.getProductoId());

        UsuarioProducto puesto = usuarioProductoDAO.findEquipadoPorCategoria(usuario.getUsuarioId(), "Tema");
        assertNotNull(puesto);
        assertEquals("TEMA_DULCE", puesto.getProducto().getCodigo());
        assertEquals(1, filas(usuario, "Tema").size());
    }

    @Test
    void laRedDeSeguridad_dejaSuFila() {
        em.flush();

        productoService.asegurarIdentidad(usuario);

        UsuarioProducto puesto = usuarioProductoDAO.findEquipadoPorCategoria(usuario.getUsuarioId(), "Tema");
        assertNotNull(puesto);
        assertEquals("TEMA_PROFUNDIDAD", puesto.getProducto().getCodigo());
    }

    @Test
    void borrarLaCuenta_conAlgoPuesto_funciona() {
        Producto a = producto("Cabeza");
        tiene(usuario, a, false);
        em.flush();
        productoService.equiparProducto(usuario, a.getProductoId());
        em.flush();
        // Como el endpoint: la baja empieza sin nada del usuario en la sesión.
        em.clear();

        usuarioService.eliminarCuenta(usuario.getUsuarioId());

        em.flush();
        em.clear();
        assertNull(em.find(Usuario.class, usuario.getUsuarioId()));
        assertEquals(List.of(), filas(usuario, "Cabeza"));
    }

    /**
     * La limpieza de la V29, ejecutada tal cual está en el fichero (menos el
     * CREATE TABLE, que en los tests hace Hibernate): quien tenía dos
     * equipados en la misma categoría se queda con el de id más alto, y lo
     * que queda equipado pasa a usuario_equipado.
     */
    @Test
    void laMigracionDeLimpieza_dejaElDeIdMasAlto_conSuFila() throws Exception {
        Producto a = producto("Cabeza");
        Producto b = producto("Cabeza");
        Producto tema = producto("Tema");
        UsuarioProducto tieneA = tiene(usuario, a, true);
        UsuarioProducto tieneB = tiene(usuario, b, true);
        UsuarioProducto tieneTema = tiene(usuario, tema, true);
        Usuario otro = nuevoUsuario();
        UsuarioProducto delOtro = tiene(otro, producto("Cabeza"), true);
        em.flush();
        // Como antes de la V29: la tabla vacía (se deshace al terminar).
        em.createNativeQuery("DELETE FROM usuario_equipado").executeUpdate();

        String sql = new ClassPathResource("db/migration/V29__usuario_equipado.sql")
                .getContentAsString(StandardCharsets.UTF_8);
        List<String> sentencias = Arrays.stream(sql.replaceAll("(?m)^--.*$", "").split(";"))
                .map(String::trim)
                .filter(s -> !s.isEmpty() && !s.startsWith("CREATE TABLE"))
                .toList();
        assertEquals(2, sentencias.size(), "la limpieza y el relleno");
        for (String sentencia : sentencias) {
            em.createNativeQuery(sentencia).executeUpdate();
        }

        assertFalse(equipado(tieneA));
        assertTrue(equipado(tieneB));
        assertTrue(equipado(tieneTema));
        assertEquals(List.of(tieneB.getUsuarioProductoId()), filas(usuario, "Cabeza"));
        assertEquals(List.of(tieneTema.getUsuarioProductoId()), filas(usuario, "Tema"));
        assertEquals(List.of(delOtro.getUsuarioProductoId()), filas(otro, "Cabeza"));
    }
}
