package com.norday;

import com.jayway.jsonpath.JsonPath;
import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.ObjetivoService;
import com.norday.habitos.service.PausaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Editar el objetivo, pausar y completar el mismo hábito a la vez, de
 * verdad: varios hilos, transacciones confirmadas en Postgres, y el PUT por
 * el controlador (open-in-view: el hábito se lee antes de llegar al servicio).
 *
 * Editar, pausar y reanudar bloquean como completar (usuario y después
 * hábito), y todos trabajan con el hábito releído tras el bloqueo: la copia
 * que se leyó antes puede ser vieja.
 *
 * La intercalación se fuerza con esperas; donde el bloqueo impide que el
 * otro confirme, la espera termina en cuanto el otro se queda esperando el
 * bloqueo (ver EsperaDeBloqueo).
 */
@SpringBootTest
@AutoConfigureMockMvc
class EditarYPausarConcurrenteTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RegistroService registroService;
    @Autowired private PausaService pausaService;
    @Autowired private UsuarioService usuarioService;
    @Autowired private MascotaService mascotaService;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private TransactionTemplate tx;
    @Autowired private javax.sql.DataSource dataSource;

    @MockitoSpyBean
    private ObjetivoService objetivoService;

    @MockitoSpyBean
    private HabitoService habitoService;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;
    private String token;

    @AfterEach
    void borrar() {
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    /** Un diario con el objetivo dado, creado hace un mes, y una Pausa en el inventario. */
    private int unDiario(Objetivo objetivo) {
        LocalDate hoy = LocalDate.now(MADRID);
        return tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Editar Concurrente");
            usuario.setUsername("test-edc-" + System.nanoTime());
            usuario.setEmail("test-edc-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("Leer", null, objetivo.frecuencia(), objetivo.meta(), usuario, null, inicio);
            habito.setUnidad(objetivo.unidad());
            habito.setMinutosObjetivo(objetivo.minutosObjetivo());
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, objetivo));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            Producto pausa = productoDAO.findByCodigo("PAUSA_HABITO");
            em.persist(new UsuarioProducto(usuario, pausa, 1));
            usuarioId = usuario.getUsuarioId();
            token = jwtUtil.generateToken(usuarioId, usuario.getEmail());
            return habito.getHabitoId();
        });
    }

    private Map<String, Object> pasoEnUnaPeticion(int habitoId, String minutos) {
        return tx.execute(estado -> registroService.completar(habitoService.buscarPorId(habitoId),
                new Solicitud(null, null, minutos, UUID.randomUUID(), OrigenRegistro.MANUAL)));
    }

    private String editar(int habitoId, String json) throws Exception {
        return mockMvc.perform(put("/api/habitos/" + habitoId)
                        .header("Authorization", "Bearer " + token)
                        .contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
    }

    private int sumaDelLibro(String origen) {
        Long suma = tx.execute(estado -> em.createQuery(
                        "SELECT COALESCE(SUM(m.cantidad), 0) FROM UsuarioMoneda m "
                                + "WHERE m.usuario.usuarioId = :u AND m.origen = :o", Long.class)
                .setParameter("u", usuarioId).setParameter("o", origen).getSingleResult());
        return suma.intValue();
    }

    private Habito leido(int habitoId) {
        return tx.execute(estado -> em.find(Habito.class, habitoId));
    }

    private int pausasQueQuedan() {
        return tx.execute(estado -> em.createQuery(
                        "SELECT up.cantidad FROM UsuarioProducto up WHERE up.usuario.usuarioId = :u "
                                + "AND up.producto.codigo = 'PAUSA_HABITO'", Integer.class)
                .setParameter("u", usuarioId).getSingleResult());
    }

    @Test
    void editarDe30A60_conUnPasoDe30EnMedio_elDiaPagaUnaVez() throws Exception {
        int habitoId = unDiario(new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null));
        mascotaService.obtenerOCrear(usuarioId);

        // El PUT se detiene al ir a programar el cambio, ya leídos los
        // registros de la semana, hasta que el paso confirme.
        CountDownLatch putEnPausa = new CountDownLatch(1);
        CountDownLatch pasoConfirmado = new CountDownLatch(1);
        doAnswer(invocacion -> {
            if (putEnPausa.getCount() > 0) {
                putEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(pasoConfirmado, dataSource);
            }
            return invocacion.callRealMethod();
        }).when(objetivoService).programar(any(), any(), any(), any());

        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<String> put = hilos.submit(() -> editar(habitoId,
                "{\"nombre\":\"Leer\",\"frecuencia\":\"DIARIO\",\"meta\":1,\"diasSemana\":null,"
                        + "\"recordatorioActivo\":true,\"unidad\":\"MINUTOS\",\"minutosObjetivo\":60}"));
        assertTrue(putEnPausa.await(30, TimeUnit.SECONDS), "el PUT llega a programar");
        Future<Map<String, Object>> paso = hilos.submit(() -> {
            Map<String, Object> resultado = pasoEnUnaPeticion(habitoId, "30");
            pasoConfirmado.countDown();
            return resultado;
        });
        String aplicaDesde = JsonPath.read(put.get(30, TimeUnit.SECONDS), "$.aplicaDesde");
        Map<String, Object> primero = paso.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        // Otro paso de 30: con el objetivo de 60, el día llega a sus minutos.
        Map<String, Object> segundo = pasoEnUnaPeticion(habitoId, "30");

        assertEquals(25, sumaDelLibro("HABITO_COMPLETADO"),
                "el día cobró " + primero.get("puntosPaso") + "+" + segundo.get("puntosPaso")
                        + "; aplicaDesde=" + aplicaDesde);
    }

    @Test
    void pausarMientrasSeCompleta_conElHabitoYaLeido_noSeCompleta() throws Exception {
        int habitoId = unDiario(new Objetivo(Frecuencia.DIARIO, 1, null));
        mascotaService.obtenerOCrear(usuarioId);

        // El completado lee el hábito (activo) como el controlador, y antes
        // de llegar al servicio se pausa y se confirma la pausa.
        CountDownLatch leido = new CountDownLatch(1);
        CountDownLatch pausaConfirmada = new CountDownLatch(1);
        ExecutorService hilos = Executors.newFixedThreadPool(2);
        Future<Map<String, Object>> completar = hilos.submit(() -> tx.execute(estado -> {
            Habito habito = habitoService.buscarPorId(habitoId);
            assertTrue(habito.isActivo());
            leido.countDown();
            try {
                pausaConfirmada.await(30, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                throw new IllegalStateException(e);
            }
            return registroService.completar(habito,
                    new Solicitud(null, null, null, UUID.randomUUID(), OrigenRegistro.MANUAL));
        }));
        assertTrue(leido.await(30, TimeUnit.SECONDS));
        pausaService.pausar(habitoId);
        pausaConfirmada.countDown();

        ExecutionException fallo = assertThrows(ExecutionException.class,
                () -> completar.get(30, TimeUnit.SECONDS), "un hábito en pausa no se completa");
        hilos.shutdown();
        assertInstanceOf(ConflictoException.class, fallo.getCause());
        assertEquals(0, sumaDelLibro("HABITO_COMPLETADO"));
    }

    @Test
    void pausarMientrasSeEdita_elPutNoLoReactiva() throws Exception {
        int habitoId = unDiario(new Objetivo(Frecuencia.DIARIO, 1, null));

        // El PUT ya ha leído el hábito en el controlador (activo); se detiene
        // al llegar al servicio mientras se pausa.
        CountDownLatch putEnPausa = new CountDownLatch(1);
        CountDownLatch pausaConfirmada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            putEnPausa.countDown();
            pausaConfirmada.await(30, TimeUnit.SECONDS);
            return invocacion.callRealMethod();
        }).when(habitoService).actualizar(any(), any(), any(), any());

        ExecutorService hilos = Executors.newSingleThreadExecutor();
        Future<String> put = hilos.submit(() -> editar(habitoId,
                "{\"nombre\":\"Leer más\",\"frecuencia\":\"DIARIO\",\"meta\":1,\"diasSemana\":null,"
                        + "\"recordatorioActivo\":true}"));
        assertTrue(putEnPausa.await(30, TimeUnit.SECONDS));
        pausaService.pausar(habitoId);
        pausaConfirmada.countDown();
        put.get(30, TimeUnit.SECONDS);
        hilos.shutdown();

        Habito despues = leido(habitoId);
        assertEquals("Leer más", despues.getNombre());
        assertFalse(despues.isActivo(), "sigue en pausa");
        assertEquals(0, pausasQueQuedan());
    }
}
