package com.norday;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.service.LimpiadorHabitos;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Dos bajas de la misma cuenta a la vez (un doble toque, un reintento): la
 * segunda espera al bloqueo del usuario, y cuando la primera confirma el
 * usuario ya no existe. Responde 404, no un fallo interno por borrar una
 * fila que ya no está.
 *
 * La primera baja se detiene después de limpiar los hábitos hasta que la
 * segunda termine o se quede esperando un bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class DosBajasConcurrenteTest {

    @Autowired private UsuarioService usuarioService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private LimpiadorHabitos limpiadorHabitos;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;

    @AfterEach
    void borrar() {
        if (usuarioId != null && tx.execute(e -> em.find(Usuario.class, usuarioId)) != null) {
            usuarioService.eliminarCuenta(usuarioId);
        }
    }

    @Test
    void dosBajasALaVez_laSegundaEsUn404() throws Exception {
        usuarioId = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Dos Bajas");
            usuario.setUsername("test-2bajas-" + System.nanoTime());
            usuario.setEmail("test-2bajas-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            return usuario.getUsuarioId();
        });
        int id = usuarioId;

        CountDownLatch primeraEnPausa = new CountDownLatch(1);
        CountDownLatch segundaTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            if (primeraEnPausa.getCount() > 0) {
                primeraEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(segundaTerminada, dataSource);
            }
            return null;
        }).when(limpiadorHabitos).limpiar(any(Usuario.class));

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<?> primera = hilos.submit(() -> {
            usuarioService.eliminarCuenta(id);
            return null;
        });
        assertTrue(primeraEnPausa.await(30, TimeUnit.SECONDS));
        Future<?> segunda = hilos.submit(() -> {
            try {
                usuarioService.eliminarCuenta(id);
                return null;
            } finally {
                segundaTerminada.countDown();
            }
        });

        primera.get(30, TimeUnit.SECONDS);
        ExecutionException fallo = assertThrows(ExecutionException.class, () -> segunda.get(30, TimeUnit.SECONDS));
        hilos.shutdown();
        assertInstanceOf(RecursoNoEncontradoException.class, fallo.getCause(),
                "la segunda baja: " + fallo.getCause());
        assertNull(tx.execute(e -> em.find(Usuario.class, id)));
    }
}
