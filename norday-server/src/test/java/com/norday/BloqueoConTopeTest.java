package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Esperar un bloqueo tiene tope, sin depender de la configuración de la base
 * de datos de producción: cada bloqueo del usuario y del hábito fija antes
 * el lock_timeout de su transacción (norday.bloqueo.espera-maxima, 10 s por
 * defecto; aquí 300 ms). Si salta, la API responde 503 con un texto
 * traducido que pide intentarlo de nuevo.
 *
 * Otra transacción tiene bloqueado al usuario y no lo suelta; un completado
 * por la API espera, agota el tope y responde 503 en el idioma del usuario.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = "norday.bloqueo.espera-maxima=300ms")
class BloqueoConTopeTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private IUsuarioDAO usuarioDAO;
    @Autowired private UsuarioService usuarioService;
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
    void conElUsuarioBloqueadoPorOtro_completarAgotaElTope_yResponde503Traducido() throws Exception {
        LocalDate hoy = LocalDate.now(MADRID);
        String[] email = new String[1];
        int habitoId = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Tope");
            usuario.setUsername("test-tope-" + System.nanoTime());
            usuario.setEmail("test-tope-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("en");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            LocalDate inicio = hoy.minusDays(30);
            Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1, usuario, null, inicio);
            em.persist(habito);
            em.persist(new HabitoObjetivo(habito, inicio, new Objetivo(Frecuencia.DIARIO, 1, null)));
            em.persist(new Racha(habito, hoy));
            em.persist(new Ritmo(habito, hoy));
            usuarioId = usuario.getUsuarioId();
            email[0] = usuario.getEmail();
            return habito.getHabitoId();
        });
        String token = jwtUtil.generateToken(usuarioId, email[0]);

        // Otra transacción bloquea al usuario y no lo suelta hasta el final
        // del test (o 15 s).
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

        try {
            mockMvc.perform(post("/api/registros/completar/" + habitoId)
                            .header("Authorization", "Bearer " + token)
                            .contentType(MediaType.APPLICATION_JSON).content("{}"))
                    .andExpect(status().isServiceUnavailable())
                    .andExpect(jsonPath("$.mensaje").value(containsString("try again")));
        } finally {
            soltar.countDown();
        }
        otra.get(30, TimeUnit.SECONDS);
        Number registros = tx.execute(e -> (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM registro WHERE habito_ref = " + habitoId).getSingleResult());
        assertEquals(0, registros.intValue(), "el completado que agotó el tope no guardó nada");
    }
}
