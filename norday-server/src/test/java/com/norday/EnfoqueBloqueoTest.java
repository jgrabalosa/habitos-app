package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UltimoAccesoService;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.service.LimpiadorHabitos;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Registrar una sesión de Norday Focus y el bloqueo del usuario: el alta de
 * la sesión bloquea al usuario (IUsuarioDAO.bloquear, con su tope de espera)
 * antes de mirar la clave y el solape.
 *
 * Sin @Transactional: cada hilo necesita ver lo que el otro confirma.
 */
@SpringBootTest
@AutoConfigureMockMvc
class EnfoqueBloqueoTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UltimoAccesoService ultimoAccesoService;
    @Autowired private TransactionTemplate tx;
    @Autowired private DataSource dataSource;

    @MockitoSpyBean
    private LimpiadorHabitos limpiadorHabitos;

    @PersistenceContext
    private EntityManager em;

    /** Los hilos del test: se cierran siempre al terminar, también si falla. */
    private ExecutorService hilos;
    private Integer usuarioId;

    @AfterEach
    void borrar() {
        if (hilos != null) hilos.shutdownNow();
        if (usuarioId != null && tx.execute(e -> em.find(Usuario.class, usuarioId)) != null) {
            usuarioService.eliminarCuenta(usuarioId);
        }
    }

    private void unUsuario(String idioma) {
        usuarioId = tx.execute(estado -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test Enfoque Bloqueo");
            usuario.setUsername("test-enfbl-" + System.nanoTime());
            usuario.setEmail("test-enfbl-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma(idioma);
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            return usuario.getUsuarioId();
        });
        // El último acceso de hoy, ya anotado: así la petición no hace su
        // UPDATE del usuario en el filtro y llega al alta de la sesión.
        ultimoAccesoService.anotar(usuarioId);
    }

    /** Una sesión de 25 min completada que terminó hace diez minutos, sin destino. */
    private MvcResult enviarSesion() throws Exception {
        Instant inicio = Instant.now().minusSeconds(600 + 1500);
        String cuerpo = "{\"clave\":\"" + UUID.randomUUID() + "\",\"inicioUtc\":\"" + inicio
                + "\",\"duracionSeg\":1500,\"duracionObjetivoMin\":25,\"estado\":\"COMPLETADA\",\"destino\":null}";
        return mockMvc.perform(post("/api/enfoque/sesiones")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(usuarioId, "x@example.com"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo))
                .andReturn();
    }

    @Test
    void laCuentaSeBorraMientrasLaSesionEsperaElBloqueo_404() throws Exception {
        unUsuario("es");
        int id = usuarioId;

        // La baja se detiene después de limpiar los hábitos, con el usuario
        // bloqueado, hasta que el alta de la sesión se quede esperando.
        CountDownLatch bajaEnPausa = new CountDownLatch(1);
        CountDownLatch peticionTerminada = new CountDownLatch(1);
        doAnswer(invocacion -> {
            invocacion.callRealMethod();
            if (bajaEnPausa.getCount() > 0) {
                bajaEnPausa.countDown();
                EsperaDeBloqueo.hastaQueTermineOEspere(peticionTerminada, dataSource);
            }
            return null;
        }).when(limpiadorHabitos).limpiar(any(Usuario.class));

        hilos = Executors.newFixedThreadPool(2);
        Future<?> baja = hilos.submit(() -> {
            usuarioService.eliminarCuenta(id);
            return null;
        });
        assertTrue(bajaEnPausa.await(30, TimeUnit.SECONDS));
        Future<MvcResult> peticion = hilos.submit(() -> {
            try {
                return enviarSesion();
            } finally {
                peticionTerminada.countDown();
            }
        });
        baja.get(30, TimeUnit.SECONDS);
        MvcResult resultado = peticion.get(30, TimeUnit.SECONDS);

        assertEquals(404, resultado.getResponse().getStatus(),
                "la cuenta ya no existe: " + resultado.getResponse().getContentAsString());
    }
}
