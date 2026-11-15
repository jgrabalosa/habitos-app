package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RachaService;
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
import static org.mockito.Mockito.doAnswer;

/**
 * Borrar un hábito a la vez que se deshace un completado suyo no se abraza.
 *
 * Deshacer bloquea al usuario y al hábito y después borra el registro, su
 * reversión y sus hitos. Borrar el hábito borra esas mismas filas y el
 * hábito el último: si no bloqueara antes, tendría las filas que deshacer
 * quiere y esperaría al hábito que deshacer tiene, y Postgres abortaría una
 * de las dos ("deadlock detected").
 *
 * La intercalación se fuerza: deshacer se detiene justo después de bloquear
 * (al calcular su "hoy") hasta que el borrado termine o se quede esperando
 * un bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class BorrarYDeshacerConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private RachaService rachaService;

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

    private static boolean esAbrazo(Throwable fallo) {
        for (Throwable t = fallo; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("deadlock")) return true;
        }
        return false;
    }

    @Test
    void borrarYDeshacerALaVez_noSeAbrazan() throws Exception {
        LocalDate hoy = LocalDate.now(MADRID);
        int habitoId = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Borrar Concurrente");
            usuario.setUsername("test-borrar-" + System.nanoTime());
            usuario.setEmail("test-borrar-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, new Objetivo(Frecuencia.DIARIO, 1, null)));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            usuarioId = usuario.getUsuarioId();
            return habito.getHabitoId();
        });
        mascotaService.obtenerOCrear(usuarioId);
        Map<String, Object> completado = tx.execute(estado -> registroService.completar(
                habitoService.buscarPorId(habitoId),
                new Solicitud(null, null, null, UUID.randomUUID(), OrigenRegistro.MANUAL)));
        int registroId = (Integer) completado.get("registroId");

        CountDownLatch deshacerEnPausa = new CountDownLatch(1);
        CountDownLatch borradoTerminado = new CountDownLatch(1);
        Thread[] hiloDeshacer = new Thread[1];
        doAnswer(invocacion -> {
            Object hoyDelHabito = invocacion.callRealMethod();
            if (Thread.currentThread() == hiloDeshacer[0] && deshacerEnPausa.getCount() > 0) {
                deshacerEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(borradoTerminado, dataSource);
            }
            return hoyDelHabito;
        }).when(rachaService).hoyDe(any(Habito.class));

        hilos = Executors.newFixedThreadPool(2);
        Future<?> deshacer = hilos.submit(() -> {
            hiloDeshacer[0] = Thread.currentThread();
            return registroService.deshacerRegistro(registroId);
        });
        assertTrue(deshacerEnPausa.await(30, TimeUnit.SECONDS), "deshacer llega a bloquear");
        Future<?> borrado = hilos.submit(() -> {
            try {
                habitoService.eliminar(habitoId);
                return null;
            } finally {
                borradoTerminado.countDown();
            }
        });

        Throwable falloDeshacer = null;
        Throwable falloBorrado = null;
        try {
            deshacer.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            falloDeshacer = e.getCause();
        }
        try {
            borrado.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            falloBorrado = e.getCause();
        }
        hilos.shutdown();

        Throwable d = falloDeshacer;
        Throwable b = falloBorrado;
        assertAll(
                () -> assertNull(d, "deshacer termina: " + d),
                () -> assertNull(b, "el borrado termina: " + b),
                () -> assertFalse(d != null && esAbrazo(d) || b != null && esAbrazo(b), "nadie muere por abrazo"));
        assertNull(tx.execute(e -> em.find(Habito.class, habitoId)), "el hábito ya no existe");
    }
}
