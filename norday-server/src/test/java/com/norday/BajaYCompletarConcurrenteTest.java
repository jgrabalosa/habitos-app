package com.norday;

import com.norday.core.exception.RecursoNoEncontradoException;
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
import com.norday.habitos.service.LimpiadorHabitos;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
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
 * La baja de la cuenta y un completado a la vez no se abrazan.
 *
 * El completado bloquea al usuario y después al hábito. La baja borra
 * primero lo que cuelga del usuario (con lo que bloquea esas filas) y al
 * final al usuario: si no bloqueara al usuario al empezar, pediría los dos
 * en el orden contrario, y con un completado esperando al hábito que ella
 * ya ha borrado, Postgres abortaría una de las dos ("deadlock detected").
 *
 * La intercalación se fuerza: la baja se detiene justo después de borrar los
 * hábitos y espera a que el completado termine o se quede esperando un
 * bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
class BajaYCompletarConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;
    @Autowired private javax.sql.DataSource dataSource;

    @MockitoSpyBean
    private LimpiadorHabitos limpiadorHabitos;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;

    /** Los hilos del test: se cierran siempre al terminar, también si falla. */
    private ExecutorService hilos;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null && tx.execute(e -> em.find(Usuario.class, usuarioId)) != null) {
            usuarioService.eliminarCuenta(usuarioId);
        }
    }

    private int unDiarioConUsuario(LocalDate hoy) {
        return tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Baja Concurrente");
            usuario.setUsername("test-baja-" + System.nanoTime());
            usuario.setEmail("test-baja-" + System.nanoTime() + "@example.com");
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
    }

    private static boolean esAbrazo(Throwable fallo) {
        for (Throwable t = fallo; t != null; t = t.getCause()) {
            if (t.getMessage() != null && t.getMessage().contains("deadlock")) return true;
        }
        return false;
    }

    @Test
    void bajaYCompletarALaVez_noSeAbrazan() throws Exception {
        int habitoId = unDiarioConUsuario(LocalDate.now(MADRID));
        mascotaService.obtenerOCrear(usuarioId);

        CountDownLatch bajaEnPausa = new CountDownLatch(1);
        CountDownLatch completarTerminado = new CountDownLatch(1);
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            // Que los borrados lleguen a Postgres ya, con sus bloqueos.
            em.flush();
            bajaEnPausa.countDown();
            EsperaDeBloqueo.hastaQueTermineOEspere(completarTerminado, dataSource);
            return null;
        }).when(limpiadorHabitos).limpiar(any(Usuario.class));

        hilos = Executors.newFixedThreadPool(2);
        int aBorrar = usuarioId;
        Future<?> baja = hilos.submit(() -> {
            usuarioService.eliminarCuenta(aBorrar);
            return null;
        });
        assertTrue(bajaEnPausa.await(30, TimeUnit.SECONDS), "la baja llega a borrar los hábitos");
        Future<?> completar = hilos.submit(() -> {
            try {
                return tx.execute(estado -> registroService.completar(habitoService.buscarPorId(habitoId),
                        new Solicitud(null, null, null, UUID.randomUUID(), OrigenRegistro.MANUAL)));
            } finally {
                completarTerminado.countDown();
            }
        });

        Throwable falloBaja = null;
        Throwable falloCompletar = null;
        try {
            baja.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            falloBaja = e.getCause();
        }
        try {
            completar.get(30, TimeUnit.SECONDS);
        } catch (ExecutionException e) {
            // El completado tiene que fallar: el hábito ya no existe.
            falloCompletar = e.getCause();
        }
        hilos.shutdown();

        Throwable bajaFallida = falloBaja;
        Throwable completarFallido = falloCompletar;
        assertAll(
                () -> assertNull(bajaFallida, "la baja termina"),
                () -> assertFalse(completarFallido != null && esAbrazo(completarFallido),
                        "el completado no muere por abrazo: " + completarFallido),
                () -> assertInstanceOf(RecursoNoEncontradoException.class, completarFallido,
                        "el completado llega tarde: el hábito ya no existe"));
        assertNull(tx.execute(e -> em.find(Usuario.class, aBorrar)), "el usuario ya no existe");
    }
}
