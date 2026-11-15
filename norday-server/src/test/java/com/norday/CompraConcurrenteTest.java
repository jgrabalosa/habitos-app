package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.LogrosHabitosService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * La tienda a la vez que otras operaciones del mismo usuario, de verdad:
 * varios hilos, transacciones confirmadas en Postgres.
 *
 * Comprar lee el saldo, comprueba que llega y apunta el gasto. Si no
 * bloquea al usuario, una compra se cuela en el «saldo después − saldo
 * antes» de un completado (y deshacer el completado la devuelve: la compra
 * sale gratis), y dos compras a la vez leen el mismo saldo y pasan las dos.
 *
 * Las intercalaciones se fuerzan con esperas hasta que el otro termine o se
 * quede esperando un bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class CompraConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private ProductoService productoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private LogroService logroService;
    @Autowired private MascotaService mascotaService;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private LogrosHabitosService logrosHabitosService;

    @MockitoSpyBean
    private UsuarioMonedaService usuarioMonedaService;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;

    /** Los hilos del test: se cierran siempre al terminar, también si falla. */
    private ExecutorService hilos;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    /** Un usuario con el saldo dado y, si se pide, un diario de 30 min. */
    private Integer unUsuario(int saldo, boolean conHabito) {
        LocalDate hoy = LocalDate.now(MADRID);
        Integer habitoId = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Compra Concurrente");
            usuario.setUsername("test-compra-" + System.nanoTime());
            usuario.setEmail("test-compra-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            usuarioId = usuario.getUsuarioId();
            usuarioMonedaService.registrarMovimiento(usuario, saldo, "TEST", 0, "Saldo de prueba");
            if (!conHabito) return null;
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("A", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            habito.setUnidad(Unidad.MINUTOS);
            habito.setMinutosObjetivo(30);
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null)));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            return habito.getHabitoId();
        });
        mascotaService.obtenerOCrear(usuarioId);
        return habitoId;
    }

    private Producto pausa() {
        return productoDAO.findByCodigo("PAUSA_HABITO");
    }

    private void comprar(int productoId) {
        tx.execute(estado -> productoService.comprarProducto(em.find(Usuario.class, usuarioId), productoId));
    }

    @Test
    void unaCompraEnMedioDeUnCompletado_deshacerloNoLaDevuelve() throws Exception {
        Producto pausa = pausa();
        int habitoId = unUsuario(pausa.getPrecio(), true);
        // PRIMEROS_PASOS ya lo tiene: así el paso solo da su parte (25).
        tx.execute(estado -> logroService.otorgarSiNoTiene(em.find(Usuario.class, usuarioId), "PRIMEROS_PASOS"));
        int saldoInicial = usuarioMonedaService.consultarSaldo(usuarioId);

        CountDownLatch pasoEnPausa = new CountDownLatch(1);
        CountDownLatch compraTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            if (pasoEnPausa.getCount() > 0) {
                pasoEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(compraTerminada, dataSource);
            }
            return invocacion.callRealMethod();
        }).when(logrosHabitosService).evaluarTrasCompletarRegistro(any(Usuario.class), any(Habito.class));

        hilos = Executors.newFixedThreadPool(2);
        Future<Map<String, Object>> paso = hilos.submit(() -> tx.execute(estado -> registroService.completar(
                habitoService.buscarPorId(habitoId),
                new Solicitud(null, null, "30", UUID.randomUUID(), OrigenRegistro.MANUAL))));
        assertTrue(pasoEnPausa.await(30, TimeUnit.SECONDS), "el paso ya ha leído su saldo de antes");
        Future<?> compra = hilos.submit(() -> {
            try {
                comprar(pausa.getProductoId());
                return null;
            } finally {
                compraTerminada.countDown();
            }
        });
        Map<String, Object> resultado = paso.get(30, TimeUnit.SECONDS);
        compra.get(30, TimeUnit.SECONDS);
        hilos.shutdown();
        assertEquals(25, resultado.get("puntosPaso"));

        Map<String, Object> deshecho = registroService.deshacerRegistro((Integer) resultado.get("registroId"));
        assertEquals(25, deshecho.get("monedasDevueltas"), "deshacer devuelve el paso, no la compra");
        assertEquals(saldoInicial - pausa.getPrecio(), usuarioMonedaService.consultarSaldo(usuarioId),
                "la compra está pagada");
    }

    @Test
    void dosComprasALaVez_conSaldoParaUna_laSegundaNoPasa() throws Exception {
        Producto pausa = pausa();
        unUsuario(pausa.getPrecio(), false);
        // Ya tuvo alguna y las gastó: comprar suma a esa fila.
        tx.execute(estado -> {
            em.persist(new UsuarioProducto(em.find(Usuario.class, usuarioId), pausa, 0));
            return null;
        });

        CountDownLatch primeraLeyo = new CountDownLatch(1);
        CountDownLatch segundaTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            Object saldo = invocacion.callRealMethod();
            if (primeraLeyo.getCount() > 0) {
                primeraLeyo.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(segundaTerminada, dataSource);
            }
            return saldo;
        }).when(usuarioMonedaService).consultarSaldo(anyInt());

        hilos = Executors.newFixedThreadPool(2);
        Future<?> primera = hilos.submit(() -> {
            comprar(pausa.getProductoId());
            return null;
        });
        assertTrue(primeraLeyo.await(30, TimeUnit.SECONDS), "la primera ha leído el saldo");
        Future<?> segunda = hilos.submit(() -> {
            try {
                comprar(pausa.getProductoId());
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

        assertEquals(1, fallos.size(), "una de las dos no tiene saldo: " + fallos);
        assertEquals("Saldo insuficiente", fallos.get(0).getMessage());
        int saldo = tx.execute(e -> usuarioMonedaService.consultarSaldo(usuarioId));
        int cantidad = tx.execute(e -> em.createQuery(
                        "SELECT up.cantidad FROM UsuarioProducto up WHERE up.usuario.usuarioId = :u "
                                + "AND up.producto.codigo = 'PAUSA_HABITO'", Integer.class)
                .setParameter("u", usuarioId).getSingleResult());
        assertEquals(0, saldo);
        assertEquals(1, cantidad);
    }
}
