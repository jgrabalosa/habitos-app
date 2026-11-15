package com.norday;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.model.dto.ResultadoLoginGoogle;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.IRegistroDAO;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doAnswer;

/**
 * Lo que se lee antes de bloquear no se escribe entero después.
 *
 * Con open-in-view, el usuario o el registro ya están en la sesión cuando el
 * servicio bloquea: si después se guarda la entidad entera, se pisa lo que
 * otro confirmó entretanto (el token de notificaciones, la nota). Y si otro
 * borró la fila mientras se esperaba, guardar revienta (500) en vez de
 * responder que ya no existe.
 *
 * Cada caso: un hilo lee como el controlador y se detiene; el otro confirma
 * su cambio; el primero sigue.
 */
@SpringBootTest
class EscriturasConcurrentesTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private UsuarioService usuarioService;
    @Autowired private IUsuarioDAO usuarioDAO;
    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private RachaService rachaService;

    @MockitoSpyBean
    private IRegistroDAO registroDAO;

    @MockitoSpyBean
    private com.norday.habitos.service.LimpiadorHabitos limpiadorHabitos;

    @PersistenceContext
    private EntityManager em;

    /** Los hilos del test: se cierran siempre al terminar, también si falla. */
    private ExecutorService hilos;
    private Integer usuarioId;
    private String email;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null && tx.execute(e -> em.find(Usuario.class, usuarioId)) != null) {
            usuarioService.eliminarCuenta(usuarioId);
        }
    }

    private void unUsuario(String proveedor) {
        tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Escrituras");
            usuario.setUsername("test-escr-" + System.nanoTime());
            usuario.setEmail("test-escr-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth(proveedor);
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            usuario.setFcmToken("token-viejo");
            em.persist(usuario);
            usuarioId = usuario.getUsuarioId();
            email = usuario.getEmail();
            return null;
        });
    }

    private Usuario leido() {
        return tx.execute(e -> em.find(Usuario.class, usuarioId));
    }

    /**
     * En un hilo, dentro de una transacción: lee el usuario (como el
     * controlador), espera a que el test confirme su cambio y hace la
     * operación. Devuelve lo que devuelve la operación.
     */
    private <T> Future<T> leerEsperarYHacer(CountDownLatch leido, CountDownLatch cambiado,
                                            java.util.function.Supplier<T> operacion) {
        hilos = Executors.newSingleThreadExecutor();
        return hilos.submit(() -> tx.execute(estado -> {
            usuarioDAO.findByEmail(email);
            leido.countDown();
            try {
                assertTrue(cambiado.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return operacion.get();
        }));
    }

    @Test
    void editarElPerfil_noPisaElTokenConfirmadoEntretanto() throws Exception {
        unUsuario("LOCAL");
        CountDownLatch leido = new CountDownLatch(1);
        CountDownLatch cambiado = new CountDownLatch(1);
        Future<Object> perfil = leerEsperarYHacer(leido, cambiado, () -> {
            Usuario cambios = new Usuario();
            cambios.setUsuarioId(usuarioId);
            cambios.setNombre("Nombre nuevo");
            usuarioService.actualizarPerfil(cambios);
            return null;
        });
        assertTrue(leido.await(30, TimeUnit.SECONDS));
        usuarioService.actualizarFcmToken(usuarioId, "token-nuevo");
        cambiado.countDown();
        perfil.get(30, TimeUnit.SECONDS);

        Usuario despues = leido();
        assertAll(
                () -> assertEquals("Nombre nuevo", despues.getNombre()),
                () -> assertEquals("token-nuevo", despues.getFcmToken(), "el token confirmado entretanto"));
    }

    @Test
    void entrarConGoogle_noPisaElTokenConfirmadoEntretanto() throws Exception {
        unUsuario("LOCAL");
        CountDownLatch leido = new CountDownLatch(1);
        CountDownLatch cambiado = new CountDownLatch(1);
        Future<ResultadoLoginGoogle> login = leerEsperarYHacer(leido, cambiado,
                () -> usuarioService.loginConGoogle(email, "Test Escrituras"));
        assertTrue(leido.await(30, TimeUnit.SECONDS));
        usuarioService.actualizarFcmToken(usuarioId, "token-nuevo");
        cambiado.countDown();
        login.get(30, TimeUnit.SECONDS);

        Usuario despues = leido();
        assertAll(
                () -> assertEquals("GOOGLE", despues.getProveedorAuth()),
                () -> assertEquals("token-nuevo", despues.getFcmToken(), "el token confirmado entretanto"));
    }

    @Test
    void entrarConGoogle_conLaCuentaBorradaMientrasEspera_esUnAltaNueva() throws Exception {
        unUsuario("GOOGLE");
        int anterior = usuarioId;

        // La baja se detiene después de limpiar los hábitos, con el usuario
        // ya bloqueado, hasta que el login se quede esperando ese bloqueo.
        CountDownLatch bajaEnPausa = new CountDownLatch(1);
        CountDownLatch loginTerminado = new CountDownLatch(1);
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            if (bajaEnPausa.getCount() > 0) {
                bajaEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(loginTerminado, dataSource);
            }
            return null;
        }).when(limpiadorHabitos).limpiar(any(Usuario.class));

        hilos = Executors.newFixedThreadPool(2);
        Future<?> baja = hilos.submit(() -> {
            usuarioService.eliminarCuenta(anterior);
            return null;
        });
        assertTrue(bajaEnPausa.await(30, TimeUnit.SECONDS));
        Future<ResultadoLoginGoogle> login = hilos.submit(() -> {
            try {
                return tx.execute(estado -> usuarioService.loginConGoogle(email, "Test Escrituras"));
            } finally {
                loginTerminado.countDown();
            }
        });
        baja.get(30, TimeUnit.SECONDS);
        ResultadoLoginGoogle resultado = login.get(30, TimeUnit.SECONDS);

        usuarioId = resultado.usuario().getUsuarioId();
        assertAll(
                () -> assertTrue(resultado.esNuevo(), "la cuenta borrada no vuelve: es un alta"),
                () -> assertNotEquals(anterior, resultado.usuario().getUsuarioId()),
                () -> assertNotNull(leido(), "la cuenta nueva existe"));
    }

    /** Un diario de veces completado hoy; devuelve el id del registro. */
    private int unRegistroDeHoy() {
        unUsuario("LOCAL");
        LocalDate hoy = LocalDate.now(MADRID);
        int habitoId = tx.execute(estado -> {
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1,
                    em.find(Usuario.class, usuarioId), null, inicio);
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, new Objetivo(Frecuencia.DIARIO, 1, null)));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            return habito.getHabitoId();
        });
        mascotaService.obtenerOCrear(usuarioId);
        Map<String, Object> completado = tx.execute(estado -> registroService.completar(
                habitoService.buscarPorId(habitoId),
                new Solicitud(null, null, null, UUID.randomUUID(), OrigenRegistro.MANUAL)));
        return (Integer) completado.get("registroId");
    }

    @Test
    void ponerNota_aUnRegistroQueSeDeshaceEntretanto_es404() throws Exception {
        int registroId = unRegistroDeHoy();

        // Deshacer se detiene justo después de bloquear (al calcular su
        // "hoy") hasta que la nota se quede esperando su bloqueo.
        CountDownLatch deshacerEnPausa = new CountDownLatch(1);
        CountDownLatch notaTerminada = new CountDownLatch(1);
        Thread[] hiloDeshacer = new Thread[1];
        doAnswer(invocacion -> {
            Object hoy = invocacion.callRealMethod();
            if (Thread.currentThread() == hiloDeshacer[0] && deshacerEnPausa.getCount() > 0) {
                deshacerEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(notaTerminada, dataSource);
            }
            return hoy;
        }).when(rachaService).hoyDe(any(Habito.class));

        hilos = Executors.newFixedThreadPool(2);
        Future<?> deshacer = hilos.submit(() -> {
            hiloDeshacer[0] = Thread.currentThread();
            return registroService.deshacerRegistro(registroId);
        });
        assertTrue(deshacerEnPausa.await(30, TimeUnit.SECONDS));
        Future<?> nota = hilos.submit(() -> {
            try {
                return tx.execute(estado -> {
                    registroService.actualizarNota(registroId, "Una nota");
                    return null;
                });
            } finally {
                notaTerminada.countDown();
            }
        });
        deshacer.get(30, TimeUnit.SECONDS);
        ExecutionException fallo = assertThrows(ExecutionException.class, () -> nota.get(30, TimeUnit.SECONDS));
        assertInstanceOf(RecursoNoEncontradoException.class, fallo.getCause(), "la nota: " + fallo.getCause());
    }

    @Test
    void valorarYPonerNotaALaVez_noSePisan() throws Exception {
        int registroId = unRegistroDeHoy();

        // La valoración lee el registro (sin nota) y se detiene hasta que la
        // nota termine o se quede esperando su bloqueo.
        CountDownLatch valoracionLeyo = new CountDownLatch(1);
        CountDownLatch notaTerminada = new CountDownLatch(1);
        Thread[] hiloValoracion = new Thread[1];
        doAnswer(invocacion -> {
            Object registro = invocacion.callRealMethod();
            if (Thread.currentThread() == hiloValoracion[0] && valoracionLeyo.getCount() > 0) {
                valoracionLeyo.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(notaTerminada, dataSource);
            }
            return registro;
        }).when(registroDAO).findById(anyInt());

        hilos = Executors.newFixedThreadPool(2);
        Future<?> valoracion = hilos.submit(() -> tx.execute(estado -> {
            hiloValoracion[0] = Thread.currentThread();
            registroService.actualizarValoracion(registroId, 4);
            return null;
        }));
        assertTrue(valoracionLeyo.await(30, TimeUnit.SECONDS));
        Future<?> nota = hilos.submit(() -> {
            try {
                return tx.execute(estado -> {
                    registroService.actualizarNota(registroId, "Una nota");
                    return null;
                });
            } finally {
                notaTerminada.countDown();
            }
        });
        nota.get(30, TimeUnit.SECONDS);
        valoracion.get(30, TimeUnit.SECONDS);

        Registro despues = tx.execute(e -> em.find(Registro.class, registroId));
        assertAll(
                () -> assertEquals("Una nota", despues.getNota()),
                () -> assertEquals(4, despues.getValoracion()));
    }
}
