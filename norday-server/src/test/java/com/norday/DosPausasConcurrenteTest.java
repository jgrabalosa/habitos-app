package com.norday;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.PausaService;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * Dos hábitos distintos del mismo usuario pausados a la vez con una sola
 * «Pausa» en el inventario: una sale bien y la otra responde 409.
 *
 * Gastar la unidad es leer la cantidad y escribirla menos uno. Si las dos
 * pausas leen antes de que escriba ninguna, las dos ven 1 y escriben 0: dos
 * hábitos pausados con una sola unidad. Pausar bloquea al usuario antes que
 * nada (HabitoService.bloquear), así que la segunda espera y lee ya 0.
 *
 * La primera pausa se detiene después de leer la cantidad hasta que la
 * segunda termine o se quede esperando un bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class DosPausasConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private PausaService pausaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private IUsuarioProductoDAO usuarioProductoDAO;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;

    @AfterEach
    void borrar() {
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    @Test
    void dosHabitosPausadosALaVez_conUnaSolaPausa_unoEsUn409() throws Exception {
        LocalDate hoy = LocalDate.now(MADRID);
        int[] ids = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Dos Pausas");
            usuario.setUsername("test-2pausas-" + System.nanoTime());
            usuario.setEmail("test-2pausas-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            int[] habitos = new int[2];
            for (int i = 0; i < 2; i++) {
                LocalDate inicio = hoy.minusDays(30);
                Habito habito = new Habito("Hábito " + i, null, Frecuencia.DIARIO, 1, usuario, null, inicio);
                em.persist(habito);
                em.persist(new HabitoObjetivo(habito, inicio, new Objetivo(Frecuencia.DIARIO, 1, null)));
                em.persist(new Racha(habito, hoy));
                em.persist(new Ritmo(habito, hoy));
                habitos[i] = habito.getHabitoId();
            }
            Producto pausa = productoDAO.findByCodigo("PAUSA_HABITO");
            em.persist(new UsuarioProducto(usuario, pausa, 1));
            usuarioId = usuario.getUsuarioId();
            return habitos;
        });

        CountDownLatch primeraLeyo = new CountDownLatch(1);
        CountDownLatch segundaTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            Object leido = invocacion.callRealMethod();
            if (primeraLeyo.getCount() > 0) {
                primeraLeyo.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(segundaTerminada, dataSource);
            }
            return leido;
        }).when(usuarioProductoDAO).findByUsuarioYProducto(anyInt(), anyInt());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<?> primera = hilos.submit(() -> {
            pausaService.pausar(ids[0]);
            return null;
        });
        assertTrue(primeraLeyo.await(30, TimeUnit.SECONDS), "la primera lee la cantidad");
        Future<?> segunda = hilos.submit(() -> {
            try {
                pausaService.pausar(ids[1]);
                return null;
            } finally {
                segundaTerminada.countDown();
            }
        });

        List<Throwable> fallos = new ArrayList<>();
        for (Future<?> pausa : List.of(primera, segunda)) {
            try {
                pausa.get(30, TimeUnit.SECONDS);
            } catch (ExecutionException e) {
                fallos.add(e.getCause());
            }
        }
        hilos.shutdown();

        assertEquals(1, fallos.size(), "una de las dos no puede: " + fallos);
        assertInstanceOf(ConflictoException.class, fallos.get(0));
        long pausados = tx.execute(e -> em.createQuery(
                        "SELECT COUNT(h) FROM Habito h WHERE h.propietario.usuarioId = :u AND h.activo = false",
                        Long.class)
                .setParameter("u", usuarioId).getSingleResult());
        assertEquals(1, pausados);
    }
}
