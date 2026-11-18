package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Oferta;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.repository.IOfertaDAO;
import com.norday.gamificacion.service.ProductoService;
import com.norday.gamificacion.service.RelojTienda;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;

/**
 * El regalo (una oferta a 0) pedido dos veces a la vez, de verdad: dos toques
 * seguidos o dos móviles con la misma cuenta. La compra bloquea al usuario
 * primero, como el resto de la tienda, así que la segunda espera y ve que ya
 * la tiene: una sola prenda en el inventario y un solo movimiento.
 *
 * La intercalación se fuerza parando a la primera justo después de leer las
 * ofertas, con el usuario ya bloqueado, hasta que la segunda termine o se
 * quede esperando el bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class RegaloConcurrenteTest {

    @Autowired private ProductoService productoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private IOfertaDAO ofertaDAO;

    @MockitoSpyBean
    private RelojTienda reloj;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;
    private Integer productoId;
    private ExecutorService hilos;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
        if (productoId != null) {
            tx.execute(estado -> {
                em.createQuery("DELETE FROM Oferta o WHERE o.producto.productoId = :p")
                        .setParameter("p", productoId).executeUpdate();
                em.createQuery("DELETE FROM Producto p WHERE p.productoId = :p")
                        .setParameter("p", productoId).executeUpdate();
                return null;
            });
        }
    }

    @Test
    void dosComprasALaVezDelRegalo_unaSolaPrenda_yUnSoloMovimiento() throws Exception {
        LocalDate hoy = LocalDate.of(2026, 12, 2);
        doReturn(Instant.parse("2026-12-02T12:00:00Z")).when(reloj).ahora();
        tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Regalo Concurrente");
            usuario.setUsername("test-regalo-" + System.nanoTime());
            usuario.setEmail("test-regalo-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            usuarioId = usuario.getUsuarioId();
            Producto gorro = new Producto("TEST_REGALO_" + System.nanoTime(), "Gorro regalo", "Para el test",
                    "Cabeza", "EQUIPABLE", 250, null);
            em.persist(gorro);
            productoId = gorro.getProductoId();
            em.persist(new Oferta(gorro, hoy.minusDays(1), hoy.plusDays(30), 0));
            return null;
        });

        CountDownLatch primeraLeyo = new CountDownLatch(1);
        CountDownLatch segundaTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            Object ofertas = invocacion.callRealMethod();
            if (primeraLeyo.getCount() > 0) {
                primeraLeyo.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(segundaTerminada, dataSource);
            }
            return ofertas;
        }).when(ofertaDAO).deProducto(anyInt());

        hilos = Executors.newFixedThreadPool(2);
        Future<?> primera = hilos.submit(() -> {
            comprar();
            return null;
        });
        assertTrue(primeraLeyo.await(30, TimeUnit.SECONDS), "la primera ha leído las ofertas");
        Future<?> segunda = hilos.submit(() -> {
            try {
                comprar();
                return null;
            } finally {
                segundaTerminada.countDown();
            }
        });
        List<Throwable> fallos = new ArrayList<>();
        for (Future<?> compra : List.of(primera, segunda)) {
            try {
                compra.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                fallos.add(e.getCause());
            }
        }
        hilos.shutdown();

        assertEquals(1, fallos.size(), "la segunda ya la tiene: " + fallos);
        assertEquals("Ya tienes este producto", fallos.get(0).getMessage());
        long prendas = tx.execute(e -> em.createQuery(
                        "SELECT COUNT(up) FROM UsuarioProducto up WHERE up.usuario.usuarioId = :u "
                                + "AND up.producto.productoId = :p", Long.class)
                .setParameter("u", usuarioId).setParameter("p", productoId).getSingleResult());
        long movimientos = tx.execute(e -> em.createQuery(
                        "SELECT COUNT(m) FROM UsuarioMoneda m WHERE m.usuario.usuarioId = :u "
                                + "AND m.referenciaId = :p", Long.class)
                .setParameter("u", usuarioId).setParameter("p", productoId).getSingleResult());
        assertEquals(1, prendas);
        assertEquals(1, movimientos);
    }

    private void comprar() {
        tx.execute(estado -> productoService.comprarProducto(em.find(Usuario.class, usuarioId), productoId));
    }
}
