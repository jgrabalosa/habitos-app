package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.repository.IMascotaDAO;
import com.norday.gamificacion.service.MascotaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * Poner nombre a la mascota o elegir la fase que se ve no pisa la XP que
 * gana a la vez. Los dos leían la mascota y la guardaban entera: la XP
 * sumada entretanto (un completado, una comida) se perdía.
 *
 * Cada caso: poner nombre (o elegir fase) lee la mascota y se detiene; otra
 * transacción le suma XP y confirma; el primero sigue.
 */
@SpringBootTest
class MascotaNombreYFaseConcurrenteTest {

    @Autowired private MascotaService mascotaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private TransactionTemplate tx;

    @MockitoSpyBean
    private IMascotaDAO mascotaDAO;

    @PersistenceContext
    private EntityManager em;

    /** Los hilos del test: se cierran siempre al terminar, también si falla. */
    private ExecutorService hilos;
    private Integer usuarioId;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    private void unUsuarioConMascota(int xp) {
        tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Mascota Concurrente");
            usuario.setUsername("test-masc-" + System.nanoTime());
            usuario.setEmail("test-masc-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            usuarioId = usuario.getUsuarioId();
            mascotaService.obtenerOCrear(usuarioId).setExperiencia(xp);
            return null;
        });
    }

    private Mascota leida() {
        return tx.execute(e -> mascotaService.obtenerOCrear(usuarioId));
    }

    /** La operación lee la mascota y se detiene hasta que se le sumen 5 XP. */
    private void conXpSumadaEnMedio(Consumer<Integer> operacion) throws Exception {
        CountDownLatch leyo = new CountDownLatch(1);
        CountDownLatch sumada = new CountDownLatch(1);
        Thread[] hiloOperacion = new Thread[1];
        doAnswer(invocacion -> {
            Object mascota = invocacion.callRealMethod();
            if (Thread.currentThread() == hiloOperacion[0] && leyo.getCount() > 0) {
                leyo.countDown();
                assertTrue(sumada.await(30, TimeUnit.SECONDS));
            }
            return mascota;
        }).when(mascotaDAO).findByUsuarioId(anyInt());

        hilos = Executors.newSingleThreadExecutor();
        Future<?> hecha = hilos.submit(() -> {
            hiloOperacion[0] = Thread.currentThread();
            operacion.accept(usuarioId);
            return null;
        });
        assertTrue(leyo.await(30, TimeUnit.SECONDS));
        tx.execute(e -> mascotaService.ganarExperiencia(usuarioId, 5));
        sumada.countDown();
        hecha.get(30, TimeUnit.SECONDS);
    }

    @Test
    void ponerNombre_noPierdeLaXpGanadaALaVez() throws Exception {
        unUsuarioConMascota(100);
        conXpSumadaEnMedio(id -> mascotaService.ponerNombre(id, "Nori Dos"));
        Mascota despues = leida();
        assertAll(
                () -> assertEquals("Nori Dos", despues.getNombre()),
                () -> assertEquals(105, despues.getExperiencia(), "la XP sumada entretanto"));
    }

    @Test
    void elegirFase_noPierdeLaXpGanadaALaVez() throws Exception {
        unUsuarioConMascota(100);
        conXpSumadaEnMedio(id -> mascotaService.elegirFase(id, "HUEVO"));
        Mascota despues = leida();
        assertAll(
                () -> assertEquals("HUEVO", despues.getFaseElegida()),
                () -> assertEquals(105, despues.getExperiencia(), "la XP sumada entretanto"));
    }
}
