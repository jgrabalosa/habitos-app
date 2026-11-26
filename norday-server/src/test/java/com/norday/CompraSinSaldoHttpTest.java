package com.norday;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.norday.core.exception.GlobalExceptionHandler;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.repository.IProductoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

/**
 * Comprar sin puntos por HTTP: 400 con el mismo cuerpo de siempre, y en el log
 * un WARN sin traza, nunca un ERROR (es un rechazo esperado, no un fallo).
 */
@SpringBootTest
@AutoConfigureMockMvc
class CompraSinSaldoHttpTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;
    @Autowired private IProductoDAO productoDAO;
    @Autowired private TransactionTemplate tx;

    @PersistenceContext
    private EntityManager em;

    private Integer usuarioId;
    private ListAppender<ILoggingEvent> log;
    private Logger logger;

    @BeforeEach
    void preparar() {
        usuarioId = tx.execute(estado -> {
            Usuario u = new Usuario();
            u.setNombre("Test Sin Saldo");
            u.setUsername("test-sin-saldo-" + System.nanoTime());
            u.setEmail("test-sin-saldo-" + System.nanoTime() + "@example.com");
            u.setContrasena("contrasenaDePrueba");
            u.setProveedorAuth("LOCAL");
            u.setFechaRegistro(LocalDateTime.now());
            u.setIdioma("es");
            u.setZonaHoraria("Europe/Madrid");
            em.persist(u);
            return u.getUsuarioId();
        });
        logger = (Logger) LoggerFactory.getLogger(GlobalExceptionHandler.class);
        log = new ListAppender<>();
        log.start();
        logger.addAppender(log);
    }

    @AfterEach
    void limpiar() {
        logger.detachAppender(log);
        if (usuarioId != null) usuarioService.eliminarCuenta(usuarioId);
    }

    @Test
    void comprarSinSaldo_da400ConElMismoCuerpo_yApuntaWarnSinTraza() throws Exception {
        Producto pausa = productoDAO.findByCodigo("PAUSA_HABITO");
        String token = jwtUtil.generateToken(usuarioId, "sin-saldo@norday.local");

        MvcResult r = mockMvc.perform(post("/api/gamificacion/productos/comprar/"
                        + usuarioId + "/" + pausa.getProductoId())
                        .header("Authorization", "Bearer " + token))
                .andReturn();

        assertEquals(400, r.getResponse().getStatus());
        assertEquals("{\"mensaje\":\"Saldo insuficiente\"}", r.getResponse().getContentAsString());

        assertTrue(log.list.stream().noneMatch(e -> e.getLevel().isGreaterOrEqual(Level.ERROR)),
                "un rechazo esperado no puede salir como ERROR");
        ILoggingEvent warn = log.list.stream()
                .filter(e -> e.getLevel() == Level.WARN).findFirst().orElse(null);
        assertNotNull(warn, "debe apuntarse un WARN");
        assertNull(warn.getThrowableProxy(), "el WARN va sin traza");
    }
}
