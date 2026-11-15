package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.PausaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una lectura que pone al día el objetivo con una copia vieja del hábito no
 * pisa lo que otros han cambiado entretanto.
 *
 * Las lecturas (Hoy, el detalle, las listas) ponen al día la copia del
 * objetivo que guarda el hábito cuando llega un cambio programado, sin
 * bloquear: son lecturas. Si guardan el hábito entero, una lectura que lo
 * leyó antes de una pausa y un cambio de nombre lo devuelve activo y con el
 * nombre de antes. Por eso solo escriben las columnas del objetivo.
 *
 * La lectura lee el hábito, se detiene, se pausa y se renombra el hábito
 * (confirmado), y la lectura sigue.
 */
@SpringBootTest
@AutoConfigureMockMvc
class ObjetivoAlDiaConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private HabitoService habitoService;
    @Autowired private PausaService pausaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private TransactionTemplate tx;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private com.norday.habitos.repository.IHabitoObjetivoDAO objetivoDAO;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;
    private String token;

    /** Los hilos del test: se cierran siempre al terminar, también si falla. */
    private ExecutorService hilos;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    /**
     * Diario de 30 min con un cambio a 45 que empieza hoy y que todavía no
     * ha llegado a la copia del hábito, y una Pausa en el inventario.
     */
    private int unDiarioDe30ConUnCambioA45Hoy() {
        LocalDate hoy = LocalDate.now(MADRID);
        return tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Objetivo al Dia");
            usuario.setUsername("test-aldia-" + System.nanoTime());
            usuario.setEmail("test-aldia-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            habito.setUnidad(Unidad.MINUTOS);
            habito.setMinutosObjetivo(30);
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null)));
            em.persist(new HabitoObjetivo(habito, hoy, new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 45, null)));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            Producto pausa = productoDAO.findByCodigo("PAUSA_HABITO");
            em.persist(new UsuarioProducto(usuario, pausa, 1));
            usuarioId = usuario.getUsuarioId();
            token = jwtUtil.generateToken(usuarioId, usuario.getEmail());
            return habito.getHabitoId();
        });
    }

    @Test
    void unaLecturaVieja_noReactivaUnHabitoPausado_niDevuelveElNombreViejo() throws Exception {
        int habitoId = unDiarioDe30ConUnCambioA45Hoy();

        CountDownLatch leido = new CountDownLatch(1);
        CountDownLatch cambiado = new CountDownLatch(1);
        hilos = Executors.newSingleThreadExecutor();
        Future<?> lectura = hilos.submit(() -> tx.execute(estado -> {
            Habito viejo = habitoService.buscarPorId(habitoId);
            leido.countDown();
            try {
                assertTrue(cambiado.await(30, TimeUnit.SECONDS));
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            habitoService.ponerObjetivoAlDia(viejo);
            return null;
        }));
        assertTrue(leido.await(30, TimeUnit.SECONDS));
        pausaService.pausar(habitoId);
        mockMvc.perform(put("/api/habitos/" + habitoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Leer más\",\"frecuencia\":\"DIARIO\",\"meta\":1,"
                                + "\"diasSemana\":null,\"recordatorioActivo\":true}"))
                .andExpect(status().isOk());
        cambiado.countDown();
        lectura.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        Habito despues = tx.execute(e -> em.find(Habito.class, habitoId));
        assertAll(
                () -> assertFalse(despues.isActivo(), "sigue en pausa"),
                () -> assertEquals("Leer más", despues.getNombre()),
                () -> assertEquals(45, despues.getMinutosObjetivo(), "el objetivo, al día"));
    }

    @Test
    void unaLecturaVieja_noDeshaceUnCambioDeObjetivoConfirmadoEntretanto() throws Exception {
        int habitoId = unDiarioDe30ConUnCambioA45Hoy();

        // La lectura lee el hábito (30) y su historial (45 desde hoy) y se
        // detiene; un PUT cambia a 60 desde hoy y confirma; la lectura sigue.
        CountDownLatch leyoHistorial = new CountDownLatch(1);
        CountDownLatch cambiado = new CountDownLatch(1);
        Thread[] hiloLectura = new Thread[1];
        org.mockito.Mockito.doAnswer(invocacion -> {
            Object versiones = invocacion.callRealMethod();
            if (Thread.currentThread() == hiloLectura[0] && leyoHistorial.getCount() > 0) {
                leyoHistorial.countDown();
                assertTrue(cambiado.await(30, TimeUnit.SECONDS));
            }
            return versiones;
        }).when(objetivoDAO).findByHabitos(org.mockito.ArgumentMatchers.anyList());

        hilos = Executors.newSingleThreadExecutor();
        Future<?> lectura = hilos.submit(() -> tx.execute(estado -> {
            hiloLectura[0] = Thread.currentThread();
            habitoService.ponerObjetivoAlDia(habitoService.buscarPorId(habitoId));
            return null;
        }));
        assertTrue(leyoHistorial.await(30, TimeUnit.SECONDS));
        mockMvc.perform(put("/api/habitos/" + habitoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"Leer\",\"frecuencia\":\"DIARIO\",\"meta\":1,\"diasSemana\":null,"
                                + "\"recordatorioActivo\":true,\"unidad\":\"MINUTOS\",\"minutosObjetivo\":60}"))
                .andExpect(status().isOk());
        cambiado.countDown();
        lectura.get(30, TimeUnit.SECONDS);

        Habito despues = tx.execute(e -> em.find(Habito.class, habitoId));
        assertEquals(60, despues.getMinutosObjetivo(), "el objetivo del PUT, no el que leyó la lectura");
    }
}
