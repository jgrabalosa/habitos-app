package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.enfoque.service.RelojEnfoque;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El premio y los logros propios de Norday Focus (16-nov-2026), con la
 * aplicación ensamblada y Postgres de verdad: el tope del día, los reintentos,
 * las consultas que cuentan días, minutos y sesiones, y el catálogo por app.
 *
 * Sin @Transactional, como SesionEnfoqueTest: el alta de la sesión es una
 * transacción confirmada. Los datos se borran con la baja de la cuenta.
 *
 * "Ahora" es el viernes 9-oct-2026 a las 10:00 de Madrid (UTC+2).
 */
@SpringBootTest
@AutoConfigureMockMvc
class PremioEnfoqueTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final Instant AHORA = VIERNES.atTime(10, 0).atZone(MADRID).toInstant();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;
    @Autowired private UsuarioMonedaService usuarioMonedaService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;

    @MockitoSpyBean
    private RelojEnfoque reloj;

    @PersistenceContext
    private EntityManager em;

    private final List<Integer> usuarios = new ArrayList<>();

    @AfterEach
    void borrar() {
        for (Integer id : usuarios) {
            usuarioService.eliminarCuenta(id);
        }
    }

    // ── Datos ────────────────────────────────────────────────────

    private int usuario() {
        doReturn(AHORA).when(reloj).ahora();
        int id = tx.execute(estado -> {
            Usuario u = new Usuario();
            u.setNombre("Test Premio Enfoque");
            u.setUsername("test-premio-" + System.nanoTime());
            u.setEmail("test-premio-" + System.nanoTime() + "@example.com");
            u.setContrasena("contrasenaDePrueba");
            u.setProveedorAuth("LOCAL");
            u.setFechaRegistro(LocalDateTime.now());
            u.setIdioma("es");
            u.setZonaHoraria("Europe/Madrid");
            em.persist(u);
            return u.getUsuarioId();
        });
        usuarios.add(id);
        mascotaService.obtenerOCrear(id);
        return id;
    }

    private String token(int usuarioId) {
        return jwtUtil.generateToken(usuarioId, "test-premio@example.com");
    }

    /** Una sesión que empieza a esa hora de Madrid, sin destino. */
    private static String cuerpo(String clave, LocalDate dia, int hora, int minuto, int minutos, String estado) {
        Instant inicio = dia.atTime(hora, minuto).atZone(MADRID).toInstant();
        return "{\"clave\":\"" + clave + "\",\"inicioUtc\":\"" + inicio + "\",\"duracionSeg\":" + minutos * 60
                + ",\"duracionObjetivoMin\":" + (estado.equals("COMPLETADA") ? minutos : 25)
                + ",\"estado\":\"" + estado + "\",\"destino\":null}";
    }

    private ResultActions enviar(int usuarioId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/enfoque/sesiones")
                .header("Authorization", "Bearer " + token(usuarioId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private ResultActions completada25(int usuarioId, LocalDate dia, int hora, int minuto) throws Exception {
        return enviar(usuarioId, cuerpo(UUID.randomUUID().toString(), dia, hora, minuto, 25, "COMPLETADA"))
                .andExpect(status().isCreated());
    }

    private int monedasDeSesiones(int usuarioId) {
        return tx.execute(estado -> ((Number) em.createNativeQuery(
                        "SELECT COALESCE(SUM(cantidad), 0) FROM usuario_moneda"
                                + " WHERE usuario_id = :u AND origen = 'SESION_ENFOQUE'")
                .setParameter("u", usuarioId).getSingleResult()).intValue());
    }

    private long movimientosDeSesiones(int usuarioId) {
        return tx.execute(estado -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM usuario_moneda WHERE usuario_id = :u AND origen = 'SESION_ENFOQUE'")
                .setParameter("u", usuarioId).getSingleResult()).longValue());
    }

    private List<String> logrosDe(int usuarioId) {
        return tx.execute(estado -> em.createNativeQuery(
                        "SELECT l.codigo FROM usuario_logro ul JOIN logro l ON l.logro_id = ul.logro_id"
                                + " WHERE ul.usuario_id = :u ORDER BY 1")
                .setParameter("u", usuarioId).getResultList().stream().map(Object::toString).toList());
    }

    private int xp(int usuarioId) {
        return mascotaService.obtenerOCrear(usuarioId).getExperiencia();
    }

    // ── El tope del día ──────────────────────────────────────────

    @Test
    void cuatroSesionesLlenanElTope_laQuintaCuentaPeroNoPaga() throws Exception {
        int a = usuario();
        int xpAntes = xp(a);

        // De 05:00 a 07:25 de Madrid, sin solaparse: 30 monedas cada una.
        completada25(a, VIERNES, 5, 0)
                .andExpect(jsonPath("$.premio.monedas").value(30))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(false));
        completada25(a, VIERNES, 5, 30).andExpect(jsonPath("$.premio.monedas").value(30));
        completada25(a, VIERNES, 6, 0).andExpect(jsonPath("$.premio.monedas").value(30));
        completada25(a, VIERNES, 6, 30)
                .andExpect(jsonPath("$.premio.monedas").value(30))
                .andExpect(jsonPath("$.premio.xp").value(5))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(true))
                .andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_TOPE")))
                .andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_CUATRO")));
        completada25(a, VIERNES, 7, 0)
                .andExpect(jsonPath("$.premio.monedas").value(0))
                .andExpect(jsonPath("$.premio.xp").value(0))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(true));

        assertEquals(120, monedasDeSesiones(a));
        assertEquals(4, movimientosDeSesiones(a), "la quinta no deja movimiento a 0");
        assertEquals(xpAntes + 20, xp(a));

        // La quinta cuenta igual para las estadísticas.
        mockMvc.perform(get("/api/enfoque/resumen").param("desde", "2026-10-09").param("hasta", "2026-10-09")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalSesiones").value(5))
                .andExpect(jsonPath("$.totalMinutos").value(125));

        List<String> logros = logrosDe(a);
        assertTrue(logros.containsAll(List.of("ENFOQUE_PRIMERA_SESION", "ENFOQUE_DIA", "ENFOQUE_CUATRO",
                "ENFOQUE_TOPE", "ENFOQUE_MANANA")), logros.toString());
        assertFalse(logros.contains("ENFOQUE_SIN_RENDIRSE"), "solo cinco sesiones");
        assertFalse(logros.contains("ENFOQUE_CON_DESTINO"), "ninguna llevaba hábito");
    }

    @Test
    void elTopeEsDeCadaDia_enLaZonaDelUsuario() throws Exception {
        int a = usuario();
        completada25(a, JUEVES, 5, 0);
        completada25(a, JUEVES, 5, 30);
        completada25(a, JUEVES, 6, 0);
        completada25(a, JUEVES, 6, 30).andExpect(jsonPath("$.premio.topeAlcanzado").value(true));
        // 23:50 del jueves en Madrid: todavía jueves, ya en el tope.
        completada25(a, JUEVES, 23, 30).andExpect(jsonPath("$.premio.monedas").value(0));
        // 00:30 del viernes: otro día, otro tope.
        completada25(a, VIERNES, 0, 30)
                .andExpect(jsonPath("$.premio.monedas").value(30))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(false));
        assertEquals(150, monedasDeSesiones(a));
    }

    // ── Reintentos y lo que no paga ──────────────────────────────

    @Test
    void elReintento_devuelveElMismoPremio_yNoPagaOtraVez() throws Exception {
        int a = usuario();
        String clave = UUID.randomUUID().toString();
        String cuerpo = cuerpo(clave, VIERNES, 8, 0, 25, "COMPLETADA");
        enviar(a, cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.premio.monedas").value(30))
                .andExpect(jsonPath("$.premio.logros[0]").value("ENFOQUE_PRIMERA_SESION"));
        int saldo = usuarioMonedaService.consultarSaldo(a);
        int xp = xp(a);

        enviar(a, cuerpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.premio.monedas").value(30))
                .andExpect(jsonPath("$.premio.xp").value(5))
                .andExpect(jsonPath("$.premio.logros[0]").value("ENFOQUE_PRIMERA_SESION"))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(false));

        assertEquals(saldo, usuarioMonedaService.consultarSaldo(a));
        assertEquals(xp, xp(a));
        assertEquals(1, movimientosDeSesiones(a));
    }

    @Test
    void unaPerdida_noPagaNiDaLogros() throws Exception {
        int a = usuario();
        int saldo = usuarioMonedaService.consultarSaldo(a);
        enviar(a, cuerpo(UUID.randomUUID().toString(), VIERNES, 6, 0, 20, "PERDIDA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premio.monedas").value(0))
                .andExpect(jsonPath("$.premio.xp").value(0))
                .andExpect(jsonPath("$.premio.logros", hasSize(0)));
        assertEquals(saldo, usuarioMonedaService.consultarSaldo(a));
        assertTrue(logrosDe(a).stream().noneMatch(c -> c.startsWith("ENFOQUE_")));
    }

    @Test
    void unaTerminada_pagaSusMinutosSinRemate() throws Exception {
        int a = usuario();
        enviar(a, cuerpo(UUID.randomUUID().toString(), VIERNES, 8, 0, 18, "TERMINADA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premio.monedas").value(18))
                .andExpect(jsonPath("$.premio.xp").value(3));
    }

    // ── Logros que miran varios días ─────────────────────────────

    @Test
    void tresDiasSeguidos_danRachaYDias_ySinRendirseLlegaALasDiez() throws Exception {
        int a = usuario();
        completada25(a, MIERCOLES, 9, 0);
        completada25(a, JUEVES, 9, 0);
        completada25(a, VIERNES, 9, 0)
                .andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_RACHA_3")))
                .andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_DIAS_3")));
        assertFalse(logrosDe(a).contains("ENFOQUE_SIN_RENDIRSE"));

        // Siete más el miércoles por la tarde: diez seguidas sin perder.
        for (int i = 0; i < 6; i++) {
            completada25(a, MIERCOLES, 12 + i, 0).andExpect(jsonPath("$.premio.logros", not(hasItem(
                    "ENFOQUE_SIN_RENDIRSE"))));
        }
        completada25(a, MIERCOLES, 18, 0).andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_SIN_RENDIRSE")));
    }

    @Test
    void unaHoraSeguida_yLasHorasTotales() throws Exception {
        int a = usuario();
        enviar(a, cuerpo(UUID.randomUUID().toString(), JUEVES, 9, 0, 120, "COMPLETADA"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.premio.monedas").value(120))
                .andExpect(jsonPath("$.premio.xp").value(24))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(true))
                .andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_SESION_60")))
                .andExpect(jsonPath("$.premio.logros", hasItem("ENFOQUE_SESION_120")));
        for (int i = 0; i < 2; i++) {
            enviar(a, cuerpo(UUID.randomUUID().toString(), VIERNES, 4 + 2 * i, 0, 90, "COMPLETADA"))
                    .andExpect(status().isCreated());
        }
        // 120 + 90 + 90 = 300 minutos: cinco horas.
        assertTrue(logrosDe(a).contains("ENFOQUE_HORAS_5"));
        assertFalse(logrosDe(a).contains("ENFOQUE_HORAS_25"));
    }

    // ── Catálogo por app ─────────────────────────────────────────

    @Test
    void losLogrosDeFocus_soloLosVeFocus() throws Exception {
        int a = usuario();
        mockMvc.perform(get("/api/gamificacion/logros/catalogo").header("X-Norday-App", "enfoque")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo =~ /ENFOQUE_.*/)]", hasSize(21)))
                .andExpect(jsonPath("$[?(@.codigo == 'RACHA_3')]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.codigo == 'MASCOTA_CRIA')]", hasSize(1)));
        mockMvc.perform(get("/api/gamificacion/logros/catalogo").header("X-Norday-App", "habitos")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.codigo =~ /ENFOQUE_.*/)]", hasSize(0)))
                .andExpect(jsonPath("$[?(@.codigo == 'RACHA_3')]", hasSize(1)));
    }
}
