package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.TextosService;
import com.norday.core.service.UltimoAccesoService;
import com.norday.core.service.UsuarioService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Una sesión de Norday Focus que agota el tope de espera del bloqueo del
 * usuario responde lo mismo que el resto de la API: 503 con
 * error.bloqueo.reintentar en el idioma del usuario (la app reintenta con
 * 5xx). EnfoqueController tiene su propio manejador de fallos técnicos, que
 * va antes que el global; para una espera de bloqueo da el mismo texto.
 *
 * Otra transacción tiene bloqueado al usuario y no lo suelta; el tope es de
 * 300 ms.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "norday.bloqueo.espera-maxima=300ms")
class EnfoqueBloqueoConTopeTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private IUsuarioDAO usuarioDAO;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UltimoAccesoService ultimoAccesoService;
    @Autowired private TextosService textosService;
    @Autowired private TransactionTemplate tx;

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

    @Test
    void conElUsuarioBloqueadoPorOtro_laSesionAgotaElTope_y503ConElTextoDelBloqueo() throws Exception {
        usuarioId = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Enfoque Tope");
            usuario.setUsername("test-enftope-" + System.nanoTime());
            usuario.setEmail("test-enftope-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("en");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            return usuario.getUsuarioId();
        });
        // El último acceso de hoy, ya anotado: la petición llega al alta de
        // la sesión sin pasar por el UPDATE del filtro.
        ultimoAccesoService.anotar(usuarioId);

        CountDownLatch bloqueado = new CountDownLatch(1);
        CountDownLatch soltar = new CountDownLatch(1);
        hilos = Executors.newSingleThreadExecutor();
        Future<?> otra = hilos.submit(() -> tx.execute(estado -> {
            assertTrue(usuarioDAO.bloquear(usuarioId));
            bloqueado.countDown();
            try {
                soltar.await(15, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return null;
        }));
        assertTrue(bloqueado.await(30, TimeUnit.SECONDS));

        String esperado = textosService.texto("error.bloqueo.reintentar", Locale.forLanguageTag("en"));
        Instant inicio = Instant.now().minusSeconds(600 + 1500);
        String clave = UUID.randomUUID().toString();
        try {
            mockMvc.perform(post("/api/enfoque/sesiones")
                            .header("Authorization", "Bearer " + jwtUtil.generateToken(usuarioId, "x@example.com"))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content("{\"clave\":\"" + clave + "\",\"inicioUtc\":\"" + inicio
                                    + "\",\"duracionSeg\":1500,\"duracionObjetivoMin\":25,"
                                    + "\"estado\":\"COMPLETADA\",\"destino\":null}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.mensaje").value(esperado));
        } finally {
            soltar.countDown();
        }
        otra.get(30, TimeUnit.SECONDS);
        Number sesiones = tx.execute(e -> (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM sesion_enfoque WHERE usuario_ref = " + usuarioId).getSingleResult());
        assertEquals(0, sesiones.intValue(), "la sesión que agotó el tope no se guardó: el reintento la creará");
    }
}
