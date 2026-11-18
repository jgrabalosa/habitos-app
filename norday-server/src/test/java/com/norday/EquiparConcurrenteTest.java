package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import com.norday.gamificacion.service.ProductoService;
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
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;

/**
 * Dos «equipar» a la vez en el mismo sitio, de verdad: quien prueba varias
 * prendas seguidas o dos móviles con la misma cuenta. Equipar bloquea al
 * usuario primero, así que el segundo espera y sustituye al primero: queda
 * una sola fila en usuario_equipado y una sola prenda con equipado = true.
 *
 * La intercalación se fuerza parando al primero después de leer lo puesto,
 * con el usuario ya bloqueado, hasta que el segundo termine o se quede
 * esperando el bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class EquiparConcurrenteTest {

    @Autowired private ProductoService productoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private IUsuarioProductoDAO usuarioProductoDAO;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;
    private List<Integer> productos;
    private ExecutorService hilos;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
        if (productos != null) {
            tx.execute(estado -> em.createQuery("DELETE FROM Producto p WHERE p.productoId IN :ids")
                    .setParameter("ids", productos).executeUpdate());
        }
    }

    @Test
    void dosEquiparALaVezEnElMismoSitio_quedaUno() throws Exception {
        int[] ids = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Equipar Concurrente");
            usuario.setUsername("test-equipar-" + System.nanoTime());
            usuario.setEmail("test-equipar-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            usuarioId = usuario.getUsuarioId();
            Producto a = new Producto("TEST_EQC_A_" + System.nanoTime(), "A", "Test", "Cabeza", "EQUIPABLE", 1, null);
            Producto b = new Producto("TEST_EQC_B_" + System.nanoTime(), "B", "Test", "Cabeza", "EQUIPABLE", 1, null);
            em.persist(a);
            em.persist(b);
            em.persist(new UsuarioProducto(usuario, a, 1));
            em.persist(new UsuarioProducto(usuario, b, 1));
            productos = List.of(a.getProductoId(), b.getProductoId());
            return new int[]{a.getProductoId(), b.getProductoId()};
        });

        CountDownLatch primeroLeyo = new CountDownLatch(1);
        CountDownLatch segundoTerminado = new CountDownLatch(1);
        doAnswer(invocacion -> {
            Object puesto = invocacion.callRealMethod();
            if (primeroLeyo.getCount() > 0) {
                primeroLeyo.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(segundoTerminado, dataSource);
            }
            return puesto;
        }).when(usuarioProductoDAO).findEquipadoPorCategoria(anyInt(), anyString());

        hilos = Executors.newFixedThreadPool(2);
        Future<?> primero = hilos.submit(() -> equipar(ids[0]));
        assertTrue(primeroLeyo.await(30, TimeUnit.SECONDS), "el primero ha leído lo puesto");
        Future<?> segundo = hilos.submit(() -> {
            try {
                equipar(ids[1]);
            } finally {
                segundoTerminado.countDown();
            }
        });
        primero.get(30, TimeUnit.SECONDS);
        segundo.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        long filas = tx.execute(e -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM usuario_equipado WHERE usuario_id = ?1 AND categoria = 'Cabeza'")
                .setParameter(1, usuarioId).getSingleResult()).longValue());
        long equipados = tx.execute(e -> em.createQuery(
                        "SELECT COUNT(up) FROM UsuarioProducto up WHERE up.usuario.usuarioId = :u AND up.equipado = true",
                        Long.class)
                .setParameter("u", usuarioId).getSingleResult());
        assertEquals(1, filas);
        assertEquals(1, equipados, "una sola con equipado = true, coherente con la tabla");
    }

    private void equipar(int productoId) {
        tx.execute(estado -> productoService.equiparProducto(em.find(Usuario.class, usuarioId), productoId));
    }
}
