package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.model.dto.HoyDTO;
import com.norday.habitos.service.CategoriaService;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.HoyService;
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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

/**
 * Lo que concede logros (y con ellos monedas) fuera de un completado no se
 * cuela en el «saldo después − saldo antes» ni en los «logros después −
 * logros antes» de un completado del mismo usuario que esté a medias. Si se
 * colara, deshacer el completado devolvería esas monedas y retiraría esos
 * logros.
 *
 * Cada caso: el paso de A se detiene después de leer su saldo y sus logros
 * de antes; en otro hilo se hace la operación que concede (abrir Hoy con un
 * ritmo alto, crear un hábito, poner la primera nota, crear la primera
 * categoría, editar el perfil); el paso sigue cuando la operación termina o
 * se queda esperando un bloqueo (ver EsperaDeBloqueo). Después se deshace A.
 */
@SpringBootTest
class ConcesionesEnUnCompletadoTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private HoyService hoyService;
    @Autowired private CategoriaService categoriaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private LogroService logroService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private LogrosHabitosService logrosHabitosService;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;
    private int habitoA;
    private int registroDeOtro;

    @AfterEach
    void borrar() {
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    /**
     * A: diario de 30 min sin nada hecho. R: diario de veces con el ritmo
     * alto (da logros de ritmo al abrir Hoy) y un registro de ayer, que sirve
     * para la nota. PRIMEROS_PASOS ya lo tiene: el paso de A solo da 25.
     */
    private void preparar() {
        LocalDate hoy = LocalDate.now(MADRID);
        tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Concesiones");
            usuario.setUsername("test-conces-" + System.nanoTime());
            usuario.setEmail("test-conces-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            LocalDate inicio = hoy.minusDays(30);

            Habito a = new Habito("A", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            a.setUnidad(Unidad.MINUTOS);
            a.setMinutosObjetivo(30);
            em.persist(a);
            em.persist(new HabitoObjetivo(a, inicio, new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null)));
            em.persist(new Racha(a, hoy));
            em.persist(new Ritmo(a, hoy));

            Habito r = new Habito("R", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            em.persist(r);
            em.persist(new HabitoObjetivo(r, inicio, new Objetivo(Frecuencia.DIARIO, 1, null)));
            em.persist(new Racha(r, hoy));
            Ritmo ritmo = new Ritmo(r, hoy);
            ritmo.setValor(0.95);
            em.persist(ritmo);
            Registro ayer = new Registro(r, true, null, hoy.minusDays(1));
            em.persist(ayer);

            em.flush();
            usuarioId = usuario.getUsuarioId();
            habitoA = a.getHabitoId();
            registroDeOtro = ayer.getRegistroId();
            return null;
        });
        tx.execute(estado -> logroService.otorgarSiNoTiene(em.find(Usuario.class, usuarioId), "PRIMEROS_PASOS"));
        mascotaService.obtenerOCrear(usuarioId);
    }

    private Usuario usuario() {
        return em.find(Usuario.class, usuarioId);
    }

    /**
     * El paso de A con la operación dada en medio; deshace A y comprueba
     * que solo devuelve lo suyo. Devuelve lo que devolvió la operación.
     */
    private <T> T conUnPasoAMedias(Supplier<T> operacion) throws Exception {
        preparar();
        CountDownLatch pasoEnPausa = new CountDownLatch(1);
        CountDownLatch operacionTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            Habito habito = invocacion.getArgument(1);
            if (habito.getHabitoId() == habitoA && pasoEnPausa.getCount() > 0) {
                pasoEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(operacionTerminada, dataSource);
            }
            return invocacion.callRealMethod();
        }).when(logrosHabitosService).evaluarTrasCompletarRegistro(any(Usuario.class), any(Habito.class));

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<Map<String, Object>> paso = hilos.submit(() -> tx.execute(estado -> registroService.completar(
                habitoService.buscarPorId(habitoA),
                new Solicitud(null, null, "30", UUID.randomUUID(), OrigenRegistro.MANUAL))));
        assertTrue(pasoEnPausa.await(30, TimeUnit.SECONDS), "el paso ya ha leído su saldo de antes");
        Future<T> otra = hilos.submit(() -> {
            try {
                return tx.execute(estado -> operacion.get());
            } finally {
                operacionTerminada.countDown();
            }
        });
        Map<String, Object> resultado = paso.get(30, TimeUnit.SECONDS);
        T devuelto = otra.get(30, TimeUnit.SECONDS);
        hilos.shutdown();
        assertEquals(25, resultado.get("puntosPaso"));

        Map<String, Object> deshecho = registroService.deshacerRegistro((Integer) resultado.get("registroId"));
        assertAll(
                () -> assertEquals(25, deshecho.get("monedasDevueltas"), "deshacer A devuelve solo el paso"),
                () -> assertEquals(List.of(), deshecho.get("logrosRetirados"), "deshacer A no retira nada"));
        return devuelto;
    }

    @Test
    void logrosDeRitmoAlAbrirHoy() throws Exception {
        HoyDTO hoy = conUnPasoAMedias(() -> hoyService.obtenerHoy(usuario(), null));
        assertFalse(hoy.logrosOtorgados().isEmpty(), "abrir Hoy da logros de ritmo");
    }

    @Test
    void logrosAlCrearUnHabito() throws Exception {
        List<String> logros = conUnPasoAMedias(() -> habitoService.crearHabito(
                new Habito("Nuevo", null, Frecuencia.DIARIO, 1, usuario(), null, null), null));
        assertFalse(logros.isEmpty(), "el tercer hábito da logros");
    }

    @Test
    void logroAlPonerLaPrimeraNota() throws Exception {
        conUnPasoAMedias(() -> {
            registroService.actualizarNota(registroDeOtro, "Primera nota");
            return null;
        });
    }

    @Test
    void logroAlCrearLaPrimeraCategoria() throws Exception {
        conUnPasoAMedias(() -> {
            categoriaService.crearCategoria(new Categoria(null, "Mía", null, null, null, false, 0, usuario()));
            return null;
        });
    }

    @Test
    void logroAlEditarElPerfil() throws Exception {
        conUnPasoAMedias(() -> {
            Usuario cambios = new Usuario();
            cambios.setUsuarioId(usuarioId);
            cambios.setNombre("Otro nombre");
            usuarioService.actualizarPerfil(cambios);
            return null;
        });
    }
}
