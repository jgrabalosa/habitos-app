package com.norday;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.PildoraCategoria;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.repository.IPildoraDAO;
import com.norday.core.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La consulta que alimenta el bucle, contra una BD de verdad. Con mocks solo se
 * comprobaría que el servicio llama al DAO; lo que se prueba aquí es que el
 * NOT EXISTS filtre lo que tiene que filtrar.
 *
 * Vive en `norday-server` y no en `conocimiento` por lo mismo que
 * `BundlesMensajesTest`: necesita la aplicación ensamblada, y este es el único
 * módulo que la tiene. El `@Transactional` deshace todo al terminar, así que no
 * deja nada escrito en la base de datos.
 */
@SpringBootTest
@Transactional
class PildoraDAOTest {

    @Autowired
    private IPildoraDAO pildoraDAO;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private Categoria categoria;

    @BeforeEach
    void prepararDatos() {
        usuario = new Usuario();
        usuario.setNombre("Test Bucle");
        usuario.setUsername("test-bucle-" + System.nanoTime());
        usuario.setEmail("test-bucle-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba"); // Usuario valida mínimo 6
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        categoria = new Categoria(
                "CAT_TEST_" + System.nanoTime(), "Categoría de prueba", null, null, null, 1);
        em.persist(categoria);
    }

    @Test
    void loYaLeidoNoVuelveAlPool() {
        Pildora leida = pildoraEnCategoria("Ya la leí");
        Pildora nueva = pildoraEnCategoria("Todavía no");

        em.persist(new UsuarioPildora(usuario, leida, EstadoPildora.VISTA, 0, LocalDateTime.now()));
        em.flush();

        List<Pildora> pool = pildoraDAO.findActivasElegibles(usuario, categoria.getCategoriaId());

        assertEquals(1, pool.size());
        assertEquals(nueva.getPildoraId(), pool.get(0).getPildoraId());
    }

    @Test
    void loGuardadoTampocoVuelveAlPool() {
        Pildora guardada = pildoraEnCategoria("En mi colección");
        Pildora nueva = pildoraEnCategoria("Todavía no");

        em.persist(new UsuarioPildora(usuario, guardada, EstadoPildora.GUARDADA, 0, LocalDateTime.now()));
        em.flush();

        List<Pildora> pool = pildoraDAO.findActivasElegibles(usuario, categoria.getCategoriaId());

        assertEquals(1, pool.size());
        assertEquals(nueva.getPildoraId(), pool.get(0).getPildoraId());
    }

    /**
     * Lo descartado sí sigue saliendo: el filtro de reaparición (3 strikes y la
     * espera) lo aplica BucleService después, en Java.
     */
    @Test
    void loDescartadoSigueEnElPool() {
        Pildora descartada = pildoraEnCategoria("La aparté");

        em.persist(new UsuarioPildora(usuario, descartada, EstadoPildora.DESCARTADA, 1, LocalDateTime.now()));
        em.flush();

        List<Pildora> pool = pildoraDAO.findActivasElegibles(usuario, categoria.getCategoriaId());

        assertEquals(1, pool.size());
        assertEquals(descartada.getPildoraId(), pool.get(0).getPildoraId());
    }

    @Test
    void lasInactivasNoSeSirvenNunca() {
        Pildora retirada = pildoraEnCategoria("Retirada del catálogo");
        retirada.setActiva(false);
        em.flush();

        List<Pildora> pool = pildoraDAO.findActivasElegibles(usuario, categoria.getCategoriaId());

        assertTrue(pool.isEmpty());
    }

    private Pildora pildoraEnCategoria(String titulo) {
        Pildora pildora = new Pildora(titulo, "preview", "contenido", null, null, true, LocalDateTime.now());
        em.persist(pildora);
        em.persist(new PildoraCategoria(pildora, categoria, true));
        return pildora;
    }
}
