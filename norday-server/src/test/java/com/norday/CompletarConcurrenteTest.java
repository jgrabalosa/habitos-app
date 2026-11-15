package com.norday;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Completados del mismo hábito a la vez, de verdad: dos hilos, dos
 * transacciones confirmadas en Postgres. El completado bloquea la fila del
 * hábito al empezar, así que se serializan: el paso y el extra no se cobran
 * dos veces. Y completar a la vez que se deshace no se bloquea en un abrazo
 * mortal (Postgres abortaría una de las dos con "deadlock detected").
 *
 * Sin @Transactional a propósito: cada hilo necesita ver lo que el otro
 * confirma. Los datos se crean con TransactionTemplate y se borran al
 * terminar con la baja de la cuenta, que pasa por todos los limpiadores.
 */
@SpringBootTest
class CompletarConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private TransactionTemplate tx;
    @Autowired private com.norday.gamificacion.service.MascotaService mascotaService;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;

    @AfterEach
    void borrar() {
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    /** Un diario de 30 min con los dos días anteriores hechos (el paso de hoy cruza el hito de 3). */
    private int unDiarioDe30(LocalDate hoy) {
        return tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Concurrente");
            usuario.setUsername("test-conc-" + System.nanoTime());
            usuario.setEmail("test-conc-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            Objetivo objetivo = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null);
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            habito.setUnidad(Unidad.MINUTOS);
            habito.setMinutosObjetivo(30);
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, objetivo));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            for (int dias = 1; dias <= 2; dias++) {
                Registro hecho = new Registro(habito, true, null, hoy.minusDays(dias));
                hecho.setCantidad(30);
                em.persist(hecho);
            }
            usuarioId = usuario.getUsuarioId();
            // La mascota ya existe: su alta concurrente es otra carrera, del motor.
            mascotaService.obtenerOCrear(usuarioId);
            return habito.getHabitoId();
        });
    }

    private int sumaDelLibro(String origen) {
        Long suma = tx.execute(estado -> em.createQuery(
                        "SELECT COALESCE(SUM(m.cantidad), 0) FROM UsuarioMoneda m "
                                + "WHERE m.usuario.usuarioId = :u AND m.origen = :o", Long.class)
                .setParameter("u", usuarioId).setParameter("o", origen).getSingleResult());
        return suma.intValue();
    }

    private Callable<Map<String, Object>> paso(int habitoId, String minutos, CountDownLatch salida) {
        return () -> {
            salida.await();
            return completarEnUnaPeticion(habitoId, minutos);
        };
    }

    /**
     * Como en la aplicación (open-in-view): el hábito se lee en la misma
     * sesión en la que se completa.
     */
    private Map<String, Object> completarEnUnaPeticion(int habitoId, String minutos) {
        return tx.execute(estado -> registroService.completar(habitoService.buscarPorId(habitoId),
                new Solicitud(null, null, minutos, UUID.randomUUID(), OrigenRegistro.MANUAL)));
    }

    @Test
    void dosPasosALaVez_elDiaSeCobraUnaSolaVez() throws Exception {
        int habitoId = unDiarioDe30(LocalDate.now(MADRID));
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        CountDownLatch salida = new CountDownLatch(1);
        Future<Map<String, Object>> a = hilos.submit(paso(habitoId, "30", salida));
        Future<Map<String, Object>> b = hilos.submit(paso(habitoId, "30", salida));
        salida.countDown();
        Map<String, Object> ra = a.get(30, TimeUnit.SECONDS);
        Map<String, Object> rb = b.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        assertEquals(25, (int) ra.get("puntosPaso") + (int) rb.get("puntosPaso"), "el día vale 25, una vez");
        assertEquals(5, (int) ra.get("xpGanada") + (int) rb.get("xpGanada"));
        assertEquals(25, sumaDelLibro("HABITO_COMPLETADO"));
        assertEquals(50, sumaDelLibro("HITO_DIAS_HECHOS"), "el hito de 3 días, una vez");
        assertEquals(60, (int) (ra.get("puntosPaso").equals(25) ? rb : ra).get("minutosDia"),
                "el segundo ve los minutos del primero");
    }

    @Test
    void completarYDeshacerALaVez_noSeAbrazan() throws Exception {
        int habitoId = unDiarioDe30(LocalDate.now(MADRID));
        Map<String, Object> primero = completarEnUnaPeticion(habitoId, "10");
        int registroId = (Integer) primero.get("registroId");

        List<Throwable> fallos = new ArrayList<>();
        for (int vuelta = 0; vuelta < 5; vuelta++) {
            ExecutorService hilos = Executors.newFixedThreadPool(2);
            CountDownLatch salida = new CountDownLatch(1);
            int aDeshacer = registroId;
            Future<?> deshacer = hilos.submit(() -> {
                salida.await();
                return registroService.deshacerRegistro(aDeshacer);
            });
            Future<Map<String, Object>> completar = hilos.submit(paso(habitoId, "10", salida));
            salida.countDown();
            for (Future<?> f : List.of(deshacer, completar)) {
                try {
                    f.get(30, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    // Un conflicto de reglas (deshacer algo que ya no es lo
                    // último) es legítimo; cualquier otra cosa, no.
                    if (!(e.getCause() instanceof ConflictoException)) fallos.add(e.getCause());
                }
            }
            hilos.shutdown();
            registroId = (Integer) completar.get().get("registroId");
        }
        assertEquals(List.of(), fallos);
    }
}
