package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Dos hábitos del mismo usuario completados a la vez, de verdad: dos hilos,
 * dos transacciones confirmadas en Postgres.
 *
 * Lo que dio un completado se guarda como "saldo después − saldo antes" y
 * "logros después − logros antes". Si otro hábito del mismo usuario confirma
 * en medio, su parte se cuela en la cuenta del primero, y deshacer el primero
 * devuelve también lo del otro y le retira sus logros. Por eso el completado
 * bloquea al usuario, y no solo al hábito.
 *
 * La intercalación se fuerza: A se detiene después de leer el saldo y los
 * logros de antes (al ir a evaluar sus logros) y espera a que B confirme,
 * como mucho unos segundos. Con el bloqueo, B no puede confirmar hasta que A
 * termina: A sigue al agotar la espera y B va después.
 *
 * Sin @Transactional, como CompletarConcurrenteTest: los datos se crean con
 * TransactionTemplate y se borran con la baja de la cuenta.
 */
@SpringBootTest
class CompletarDosHabitosConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final long ESPERA_MAXIMA_SEGUNDOS = 3;

    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private LogroService logroService;
    @Autowired private UsuarioMonedaService usuarioMonedaService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;

    @MockitoSpyBean
    private LogrosHabitosService logrosHabitosService;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;

    @AfterEach
    void borrar() {
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    private Usuario unUsuario() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Dos Habitos");
        usuario.setUsername("test-dos-" + System.nanoTime());
        usuario.setEmail("test-dos-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        usuarioId = usuario.getUsuarioId();
        return usuario;
    }

    /** Un diario de 30 min con los días anteriores hechos que se pidan. */
    private int unDiarioDe30(Usuario usuario, String nombre, LocalDate hoy, int diasPreviosHechos) {
        Objetivo objetivo = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null);
        LocalDate inicio = hoy.minusDays(30);
        Habito habito = new Habito(nombre, null, Frecuencia.DIARIO, 1, usuario, null, inicio);
        habito.setUnidad(Unidad.MINUTOS);
        habito.setMinutosObjetivo(30);
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, inicio, objetivo));
        em.persist(new Racha(habito, hoy));
        em.persist(new Ritmo(habito, hoy));
        for (int dias = 1; dias <= diasPreviosHechos; dias++) {
            Registro hecho = new Registro(habito, true, null, hoy.minusDays(dias));
            hecho.setCantidad(30);
            em.persist(hecho);
        }
        return habito.getHabitoId();
    }

    private Map<String, Object> completarEnUnaPeticion(int habitoId) {
        return tx.execute(estado -> registroService.completar(habitoService.buscarPorId(habitoId),
                new Solicitud(null, null, "30", UUID.randomUUID(), OrigenRegistro.MANUAL)));
    }

    private boolean tiene(String codigo) {
        return Boolean.TRUE.equals(tx.execute(estado -> logroService
                .logrosDeUsuario(em.find(Usuario.class, usuarioId)).stream()
                .anyMatch(ul -> codigo.equals(ul.getLogro().getCodigo()))));
    }

    @Test
    void deshacerA_devuelveSoloLoDeA_yNoRetiraLosLogrosDeB() throws Exception {
        LocalDate hoy = LocalDate.now(MADRID);
        int[] ids = tx.execute(estado -> {
            Usuario usuario = unUsuario();
            // A no tiene nada antes; B lleva dos días hechos, así que su paso
            // de hoy cruza los 3 días: hito (50) y RACHA_3.
            int a = unDiarioDe30(usuario, "A", hoy, 0);
            int b = unDiarioDe30(usuario, "B", hoy, 2);
            return new int[] {a, b};
        });
        int habitoA = ids[0];
        int habitoB = ids[1];
        // PRIMEROS_PASOS ya lo tiene: así el paso de A solo da su parte (25).
        tx.execute(estado -> logroService.otorgarSiNoTiene(em.find(Usuario.class, usuarioId), "PRIMEROS_PASOS"));
        // La mascota ya existe: su alta concurrente es otra carrera, del motor.
        mascotaService.obtenerOCrear(usuarioId);

        CountDownLatch aEnPausa = new CountDownLatch(1);
        CountDownLatch bConfirmado = new CountDownLatch(1);
        doAnswer(invocacion -> {
            Habito habito = invocacion.getArgument(1);
            if (habito.getHabitoId() == habitoA && aEnPausa.getCount() > 0) {
                aEnPausa.countDown();
                bConfirmado.await(ESPERA_MAXIMA_SEGUNDOS, TimeUnit.SECONDS);
            }
            return invocacion.callRealMethod();
        }).when(logrosHabitosService).evaluarTrasCompletarRegistro(any(Usuario.class), any(Habito.class));

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<Map<String, Object>> pasoA = hilos.submit(() -> completarEnUnaPeticion(habitoA));
        assertTrue(aEnPausa.await(30, TimeUnit.SECONDS), "A llega a evaluar sus logros");
        Future<Map<String, Object>> pasoB = hilos.submit(() -> {
            Map<String, Object> resultado = completarEnUnaPeticion(habitoB);
            bConfirmado.countDown();
            return resultado;
        });
        Map<String, Object> ra = pasoA.get(30, TimeUnit.SECONDS);
        Map<String, Object> rb = pasoB.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        assertEquals(25, ra.get("puntosPaso"));
        assertEquals(List.of(), ra.get("logros"), "el paso de A no da ningún logro");
        assertEquals(50, rb.get("puntosHito"));
        assertTrue(((List<?>) rb.get("logros")).contains("RACHA_3"), "RACHA_3 es de B");

        int saldoAntesDeDeshacer = usuarioMonedaService.consultarSaldo(usuarioId);
        Map<String, Object> deshecho = registroService.deshacerRegistro((Integer) ra.get("registroId"));

        int saldoDespues = usuarioMonedaService.consultarSaldo(usuarioId);
        boolean conservaRacha3 = tiene("RACHA_3");
        assertAll(
                () -> assertEquals(25, deshecho.get("monedasDevueltas"), "deshacer A"),
                () -> assertEquals(List.of(), deshecho.get("logrosRetirados"), "deshacer A no retira nada"),
                () -> assertTrue(conservaRacha3, "B conserva su logro"),
                () -> assertEquals(saldoAntesDeDeshacer - 25, saldoDespues));
    }
}
