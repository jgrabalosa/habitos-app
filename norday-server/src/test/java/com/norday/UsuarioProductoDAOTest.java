package com.norday;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.junit.jupiter.api.Assertions.*;

/**
 * `poseeAlgunoDeCategoria` contra una BD de verdad. Con mocks solo se
 * comprobaría que el servicio llama al DAO; lo que se prueba aquí es que las
 * dos cláusulas del WHERE (usuario Y categoría) filtran lo que tienen que
 * filtrar — se descubrió que la consulta era válida al ejecutarla contra
 * Postgres en staging, no antes.
 *
 * Vive en `norday-server` y no en `gamificacion` por lo mismo que
 * `PildoraDAOTest`: necesita la aplicación ensamblada, y este es el único
 * módulo que la tiene. El `@Transactional` deshace todo al terminar.
 */
@SpringBootTest
@Transactional
class UsuarioProductoDAOTest {

    @Autowired
    private IUsuarioProductoDAO usuarioProductoDAO;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;

    @BeforeEach
    void prepararDatos() {
        usuario = new Usuario();
        usuario.setNombre("Test Producto");
        usuario.setUsername("test-producto-" + System.nanoTime());
        usuario.setEmail("test-producto-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba"); // Usuario valida mínimo 6
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
    }

    @Test
    void usuarioSinNingunProducto() {
        em.flush();

        boolean posee = usuarioProductoDAO.poseeAlgunoDeCategoria(usuario.getUsuarioId(), "Tema");

        assertFalse(posee);
    }

    @Test
    void usuarioConUnProductoDeTema() {
        adquirirProducto(usuario, "Tema");
        em.flush();

        boolean posee = usuarioProductoDAO.poseeAlgunoDeCategoria(usuario.getUsuarioId(), "Tema");

        assertTrue(posee);
    }

    @Test
    void usuarioConAvatarPeroNingunTema() {
        adquirirProducto(usuario, "Avatar");
        em.flush();

        boolean posee = usuarioProductoDAO.poseeAlgunoDeCategoria(usuario.getUsuarioId(), "Tema");

        assertFalse(posee);
    }

    @Test
    void otroUsuarioTieneTemaPeroElNuestroNo() {
        Usuario otro = new Usuario();
        otro.setNombre("Otro Usuario");
        otro.setUsername("test-otro-" + System.nanoTime());
        otro.setEmail("test-otro-" + System.nanoTime() + "@example.com");
        otro.setContrasena("contrasenaDePrueba");
        otro.setProveedorAuth("LOCAL");
        otro.setFechaRegistro(LocalDateTime.now());
        otro.setIdioma("es");
        otro.setZonaHoraria("Europe/Madrid");
        em.persist(otro);

        adquirirProducto(otro, "Tema");
        em.flush();

        boolean posee = usuarioProductoDAO.poseeAlgunoDeCategoria(usuario.getUsuarioId(), "Tema");

        assertFalse(posee);
    }

    @Test
    void usuarioConDosProductosDeTema() {
        adquirirProducto(usuario, "Tema");
        adquirirProducto(usuario, "Tema");
        em.flush();

        boolean posee = usuarioProductoDAO.poseeAlgunoDeCategoria(usuario.getUsuarioId(), "Tema");

        assertTrue(posee);
    }

    private void adquirirProducto(Usuario propietario, String categoria) {
        Producto producto = new Producto(
                "COD_TEST_" + System.nanoTime(),
                "Producto de prueba",
                "Descripción de prueba",
                categoria,
                "PERMANENTE",
                100,
                null);
        em.persist(producto);

        UsuarioProducto usuarioProducto = new UsuarioProducto(propietario, producto, 1);
        usuarioProducto.setFechaAdquirido(LocalDateTime.now(ZoneOffset.UTC));
        em.persist(usuarioProducto);
    }
}
