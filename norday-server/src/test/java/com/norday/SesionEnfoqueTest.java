package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.enfoque.service.RelojEnfoque;
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
import com.norday.habitos.service.DestinoMinutosHabitos;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RachaService;
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
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doCallRealMethod;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Norday Focus de punta a punta: la aplicación ensamblada, Postgres de verdad
 * y los dos módulos (enfoque y habitos) hablando solo por el puerto del motor.
 *
 * Sin @Transactional a propósito: registrar una sesión son tres transacciones
 * confirmadas (guardar, aportar, apuntar el resultado), y lo que se prueba es
 * justo que la sesión sobrevive a un rechazo del hábito. Con la transacción de
 * test envolviéndolo todo no se probaría nada. Los datos se crean con
 * TransactionTemplate y se borran con la baja de la cuenta, que pasa por todos
 * los limpiadores (también LimpiadorEnfoque, con la FK real).
 *
 * "Ahora" es el viernes 9-oct-2026 a las 10:00 de Madrid (RelojEnfoque) y
 * "hoy" de los hábitos, ese viernes (RachaService.hoyDe).
 */
@SpringBootTest
@AutoConfigureMockMvc
class SesionEnfoqueTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 11);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 12);
    private static final Instant AHORA = VIERNES.atTime(10, 0).atZone(MADRID).toInstant();

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;
    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private UsuarioMonedaService usuarioMonedaService;
    @Autowired private MascotaService mascotaService;
    @Autowired private TransactionTemplate tx;

    @MockitoSpyBean
    private RachaService rachaService;

    @MockitoSpyBean
    private RelojEnfoque reloj;

    /** Sin stubs se comporta como el de verdad; los tests de fallo técnico le hacen lanzar. */
    @MockitoSpyBean
    private DestinoMinutosHabitos destinoHabitos;

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

    private void ahora(Instant ahora, LocalDate hoy) {
        doReturn(ahora).when(reloj).ahora();
        doReturn(hoy).when(rachaService).hoyDe(any(Habito.class));
    }

    private int usuario() {
        ahora(AHORA, VIERNES);
        int id = tx.execute(estado -> {
            Usuario u = new Usuario();
            u.setNombre("Test Enfoque");
            u.setUsername("test-enf-" + System.nanoTime());
            u.setEmail("test-enf-" + System.nanoTime() + "@example.com");
            u.setContrasena("contrasenaDePrueba");
            u.setProveedorAuth("LOCAL");
            u.setFechaRegistro(LocalDateTime.now());
            u.setIdioma("es");
            u.setZonaHoraria("Europe/Madrid");
            em.persist(u);
            return u.getUsuarioId();
        });
        usuarios.add(id);
        // La mascota ya existe: su alta no es lo que se prueba aquí.
        mascotaService.obtenerOCrear(id);
        return id;
    }

    private int habito(int usuarioId, String nombre, Objetivo objetivo, boolean activo) {
        return tx.execute(estado -> {
            Usuario u = em.find(Usuario.class, usuarioId);
            Habito h = new Habito(nombre, null, objetivo.frecuencia(), objetivo.meta(), u, null, INICIO);
            h.setUnidad(objetivo.unidad());
            h.setMinutosObjetivo(objetivo.minutosObjetivo());
            h.setActivo(activo);
            em.persist(h);
            em.persist(new HabitoObjetivo(h, INICIO, objetivo));
            em.persist(new Racha(h, INICIO));
            em.persist(new Ritmo(h, INICIO));
            return h.getHabitoId();
        });
    }

    private static Objetivo minutosDiario(int minutos) {
        return new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, minutos, null);
    }

    private static Objetivo vecesDiario() {
        return new Objetivo(Unidad.VECES, Frecuencia.DIARIO, 1, null, null);
    }

    private String token(int usuarioId) {
        return jwtUtil.generateToken(usuarioId, "test-enf@example.com");
    }

    private int saldo(int usuarioId) {
        return usuarioMonedaService.consultarSaldo(usuarioId);
    }

    private int xp(int usuarioId) {
        return mascotaService.obtenerOCrear(usuarioId).getExperiencia();
    }

    /** Los movimientos de monedas del usuario, como "cantidad ORIGEN", ordenados. */
    private List<String> movimientos(int usuarioId) {
        return tx.execute(estado -> em.createNativeQuery(
                        "SELECT cantidad || ' ' || origen FROM usuario_moneda WHERE usuario_id = :u ORDER BY 1")
                .setParameter("u", usuarioId).getResultList().stream().map(Object::toString).toList());
    }

    /**
     * Los movimientos que no son de Norday Focus: sin el premio de las
     * sesiones (SESION_ENFOQUE) ni los logros propios de Focus. Es lo que paga
     * el hábito, más lo que no tiene que ver con Focus.
     */
    private static final String SIN_FOCUS = " FROM usuario_moneda m"
            + " LEFT JOIN logro l ON m.origen = 'LOGRO' AND l.logro_id = m.referencia_id"
            + " WHERE m.usuario_id = :u AND m.origen <> 'SESION_ENFOQUE'"
            + " AND (l.origen_app IS NULL OR l.origen_app <> 'enfoque')";

    private List<String> movimientosSinFocus(int usuarioId) {
        return tx.execute(estado -> em.createNativeQuery(
                        "SELECT m.cantidad || ' ' || m.origen" + SIN_FOCUS + " ORDER BY 1")
                .setParameter("u", usuarioId).getResultList().stream().map(Object::toString).toList());
    }

    private int saldoSinFocus(int usuarioId) {
        return tx.execute(estado -> ((Number) em.createNativeQuery("SELECT COALESCE(SUM(m.cantidad), 0)" + SIN_FOCUS)
                .setParameter("u", usuarioId).getSingleResult()).intValue());
    }

    /** Las monedas del premio de las sesiones (SESION_ENFOQUE), sin los logros. */
    private int monedasDeSesiones(int usuarioId) {
        return tx.execute(estado -> ((Number) em.createNativeQuery(
                        "SELECT COALESCE(SUM(cantidad), 0) FROM usuario_moneda"
                                + " WHERE usuario_id = :u AND origen = 'SESION_ENFOQUE'")
                .setParameter("u", usuarioId).getSingleResult()).intValue());
    }

    /** Los códigos de los logros del usuario. */
    private List<String> logrosDe(int usuarioId) {
        return tx.execute(estado -> em.createNativeQuery(
                        "SELECT l.codigo FROM usuario_logro ul JOIN logro l ON l.logro_id = ul.logro_id"
                                + " WHERE ul.usuario_id = :u ORDER BY 1")
                .setParameter("u", usuarioId).getResultList().stream().map(Object::toString).toList());
    }

    private List<Registro> registros(int habitoId) {
        return tx.execute(estado -> em.createQuery(
                        "SELECT r FROM Registro r WHERE r.habito.habitoId = :h", Registro.class)
                .setParameter("h", habitoId).getResultList());
    }

    private Object[] filaSesion(String clave) {
        return tx.execute(estado -> (Object[]) em.createNativeQuery(
                        "SELECT aporte_estado, aporte_motivo, minutos_aportados, dia, estado"
                                + " FROM sesion_enfoque WHERE clave = CAST(:c AS UUID)")
                .setParameter("c", clave).getSingleResult());
    }

    private long sesionesDe(int usuarioId) {
        return tx.execute(estado -> ((Number) em.createNativeQuery(
                        "SELECT COUNT(*) FROM sesion_enfoque WHERE usuario_ref = :u")
                .setParameter("u", usuarioId).getSingleResult()).longValue());
    }

    private static String cuerpo(String clave, Instant inicio, int duracionSeg, int objetivoMin, String estado,
                                 Integer habitoId) {
        String destino = habitoId == null ? "null" : "{\"tipo\":\"habito\",\"id\":" + habitoId + "}";
        return "{\"clave\":\"" + clave + "\",\"inicioUtc\":\"" + inicio + "\",\"duracionSeg\":" + duracionSeg
                + ",\"duracionObjetivoMin\":" + objetivoMin + ",\"estado\":\"" + estado + "\",\"destino\":"
                + destino + "}";
    }

    /** Una sesión de 25 min completada que termina a las 9:50 de Madrid del viernes. */
    private static String sesion25(String clave, Integer habitoId) {
        return cuerpo(clave, AHORA.minusSeconds(600 + 1500), 1500, 25, "COMPLETADA", habitoId);
    }

    private ResultActions enviar(int usuarioId, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/enfoque/sesiones")
                .header("Authorization", "Bearer " + token(usuarioId))
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    // ── Aporte a un hábito de minutos ────────────────────────────

    @Test
    void conHabitoDeMinutos_sumaLosMinutos_yPagaLoMismoQueElPanel() throws Exception {
        // A: la sesión de Norday Focus. B: el mismo hábito y los mismos
        // minutos desde el panel de Norday Habits (completar directo).
        int a = usuario();
        int leerA = habito(a, "Leer", minutosDiario(30), true);
        int b = usuario();
        int leerB = habito(b, "Leer", minutosDiario(30), true);
        int saldoA = saldo(a);
        int xpA = xp(a);
        int saldoB = saldo(b);
        int xpB = xp(b);
        String clave = UUID.randomUUID().toString();

        String respuesta = enviar(a, sesion25(clave, leerA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.clave").value(clave))
                .andExpect(jsonPath("$.sesion.dia").value("2026-10-09"))
                .andExpect(jsonPath("$.sesion.estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.sesion.minutos").value(25))
                .andExpect(jsonPath("$.sesion.destino.tipo").value("habito"))
                .andExpect(jsonPath("$.sesion.destino.id").value(leerA))
                .andExpect(jsonPath("$.aporte.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.aporte.minutos").value(25))
                .andExpect(jsonPath("$.aporte.minutosDia").value(25))
                .andExpect(jsonPath("$.aporte.minutosObjetivo").value(30))
                .andExpect(jsonPath("$.aporte.diaHecho").value(false))
                .andReturn().getResponse().getContentAsString();

        Map<String, Object> panel = tx.execute(estado -> registroService.completar(habitoService.buscarPorId(leerB),
                new Solicitud(null, VIERNES, "25", UUID.randomUUID(), OrigenRegistro.MANUAL)));

        // Lo del hábito es lo mismo que desde el panel; la sesión paga además
        // lo suyo (30 monedas y 5 XP), que se suma.
        int monedasHabito = saldoSinFocus(a) - saldoA;
        int monedasPanel = saldo(b) - saldoB;
        assertTrue(monedasPanel > 0, "el panel paga algo por 25 de 30 minutos");
        assertEquals(monedasPanel, monedasHabito, "el hábito paga lo mismo que desde el panel");
        assertEquals(30, monedasDeSesiones(a), "y la sesión paga lo suyo aparte");
        assertEquals(xp(b) - xpB + 5, xp(a) - xpA, "la misma XP del hábito, más la de la sesión");
        assertTrue(respuesta.contains("\"premio\":{\"monedas\":30,\"xp\":5,"), respuesta);
        assertTrue(respuesta.contains("\"monedas\":" + panel.get("puntosGanados") + ","),
                "la respuesta dice las monedas del hábito, como puntosGanados en el panel: " + respuesta);
        assertTrue(respuesta.contains("\"xp\":" + panel.get("xpGanada") + ","), respuesta);
        assertEquals(movimientos(b), movimientosSinFocus(a),
                "los mismos movimientos del hábito en el libro, logros incluidos");

        List<Registro> suyos = registros(leerA);
        assertEquals(1, suyos.size());
        assertEquals(25, suyos.get(0).getCantidad());
        assertEquals(OrigenRegistro.ENFOQUE, suyos.get(0).getOrigen());
        assertEquals(UUID.fromString(clave), suyos.get(0).getClaveCliente(), "la clave de la sesión viaja al registro");
        assertEquals(VIERNES, suyos.get(0).getFecha());

        Object[] fila = filaSesion(clave);
        assertEquals("ACEPTADO", fila[0]);
        assertNull(fila[1]);
        assertEquals(25, ((Number) fila[2]).intValue());
    }

    /** Las monedas de la respuesta son las del hábito (paso, extra e hito); las de los logros van aparte. */
    @Test
    void laRespuesta_llevaLasMonedasDelHabito_yLaXp() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        int xpAntes = xp(a);
        String respuesta = enviar(a, sesion25(UUID.randomUUID().toString(), leer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aporte.logros[0]").value("PRIMEROS_PASOS"))
                .andReturn().getResponse().getContentAsString();
        int delHabito = movimientos(a).stream().filter(m -> !m.endsWith(" LOGRO") && !m.endsWith(" SESION_ENFOQUE"))
                .mapToInt(m -> Integer.parseInt(m.substring(0, m.indexOf(' ')))).sum();
        assertTrue(delHabito > 0);
        assertTrue(respuesta.contains("\"aporte\":{\"estado\":\"ACEPTADO\""), respuesta);
        assertTrue(respuesta.contains("\"monedas\":" + delHabito + ","), respuesta);
        // La XP del hábito: la total menos los 5 de la sesión.
        assertTrue(respuesta.contains("\"xp\":" + (xp(a) - xpAntes - 5) + ","), respuesta);
    }

    // ── Sin destino ──────────────────────────────────────────────

    @Test
    void sinDestino_seGuarda_pagaSuPremio_yNoTocaElHabito() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        int saldoAntes = saldo(a);
        int xpAntes = xp(a);
        String clave = UUID.randomUUID().toString();

        enviar(a, sesion25(clave, null))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.minutos").value(25))
                .andExpect(jsonPath("$.sesion.destino").isEmpty())
                .andExpect(jsonPath("$.aporte.estado").value("SIN_DESTINO"))
                .andExpect(jsonPath("$.aporte.minutos").value(0))
                .andExpect(jsonPath("$.aporte.monedas").value(0))
                .andExpect(jsonPath("$.aporte.xp").value(0))
                .andExpect(jsonPath("$.premio.monedas").value(30))
                .andExpect(jsonPath("$.premio.xp").value(5))
                .andExpect(jsonPath("$.premio.logros", hasSize(1)))
                .andExpect(jsonPath("$.premio.logros[0]").value("ENFOQUE_PRIMERA_SESION"))
                .andExpect(jsonPath("$.premio.topeAlcanzado").value(false));

        assertEquals(saldoAntes, saldoSinFocus(a), "el hábito no paga nada");
        assertEquals(30, monedasDeSesiones(a), "sin hábito, la sesión paga lo suyo");
        assertEquals(saldoAntes + 30 + 50, saldo(a), "más los 50 de ENFOQUE_PRIMERA_SESION");
        assertEquals(xpAntes + 5, xp(a));
        assertTrue(registros(leer).isEmpty());
        assertEquals("SIN_DESTINO", filaSesion(clave)[0]);
    }

    @Test
    void perdida_conDestino_seGuarda_peroNuncaSuma() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();

        enviar(a, cuerpo(clave, AHORA.minusSeconds(1800), 900, 25, "PERDIDA", leer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sesion.minutos").value(0))
                .andExpect(jsonPath("$.aporte.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.aporte.motivo").value("enfoque.aporte.sesion.perdida"))
                .andExpect(jsonPath("$.aporte.motivoTexto").value("Una sesión perdida no suma minutos."));
        assertTrue(registros(leer).isEmpty());
    }

    // ── Rechazos del hábito: la sesión se guarda igual ───────────

    private void rechazada(int usuarioId, int habitoId, String clave, String cuerpo, String motivo) throws Exception {
        int saldoAntes = saldoSinFocus(usuarioId);
        enviar(usuarioId, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aporte.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.aporte.motivo").value(motivo))
                .andExpect(jsonPath("$.aporte.minutos").value(0))
                .andExpect(jsonPath("$.aporte.monedas").value(0));
        Object[] fila = filaSesion(clave);
        assertEquals("RECHAZADO", fila[0], "la sesión queda guardada con el rechazo");
        assertEquals(motivo, fila[1]);
        assertEquals(0, ((Number) fila[2]).intValue());
        assertTrue(registros(habitoId).isEmpty(), "el hábito no recibe nada");
        assertEquals(saldoAntes, saldoSinFocus(usuarioId), "el hábito no paga nada");
        assertTrue(monedasDeSesiones(usuarioId) > 0, "la sesión sí paga lo suyo, aunque el hábito la rechace");
    }

    @Test
    void rechazo_habitoEnPausa() throws Exception {
        int a = usuario();
        int pausado = habito(a, "Leer", minutosDiario(30), false);
        String clave = UUID.randomUUID().toString();
        rechazada(a, pausado, clave, sesion25(clave, pausado), "aporte.habito.pausado");
    }

    @Test
    void rechazo_habitoDeVeces() throws Exception {
        int a = usuario();
        int veces = habito(a, "Meditar", vecesDiario(), true);
        String clave = UUID.randomUUID().toString();
        rechazada(a, veces, clave, sesion25(clave, veces), "aporte.habito.no.minutos");
    }

    @Test
    void rechazo_semanaPasada_sesionDelDomingoEnviadaElLunes() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        // El lunes a las 10:00 llega una sesión que empezó el domingo a las
        // 23:20 de Madrid: su día es el domingo, de la semana pasada.
        ahora(LUNES.atTime(10, 0).atZone(MADRID).toInstant(), LUNES);
        String clave = UUID.randomUUID().toString();
        Instant inicio = DOMINGO.atTime(23, 20).atZone(MADRID).toInstant();
        rechazada(a, leer, clave, cuerpo(clave, inicio, 1800, 30, "COMPLETADA", leer), "aporte.habito.semana.pasada");
        assertEquals(java.sql.Date.valueOf(DOMINGO), filaSesion(clave)[3], "el día es el de inicio, en Madrid");
    }

    @Test
    void rechazo_habitoAjeno_dice_lo_mismo_que_uno_que_no_existe() throws Exception {
        int a = usuario();
        int b = usuario();
        int deB = habito(b, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        rechazada(a, deB, clave, sesion25(clave, deB), "aporte.habito.no.encontrado");
    }

    /**
     * El rechazo que solo descubre completar, ya dentro de su transacción: la
     * clave de la sesión ya la usó un registro de OTRO hábito y completar lanza
     * ConflictoException. La sesión tiene que quedar guardada con el rechazo:
     * si el aporte compartiera transacción con ella, el commit reventaría con
     * UnexpectedRollbackException y la sesión se perdería.
     */
    @Test
    void rechazoDentroDeCompletar_noSeLlevaPorDelanteLaSesion() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        int otro = habito(a, "Escribir", minutosDiario(30), true);
        UUID clave = UUID.randomUUID();
        tx.execute(estado -> registroService.completar(habitoService.buscarPorId(otro),
                new Solicitud(null, VIERNES, "10", clave, OrigenRegistro.MANUAL)));
        int saldoAntes = saldoSinFocus(a);

        enviar(a, sesion25(clave.toString(), leer))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aporte.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.aporte.motivo").value("aporte.habito.conflicto"))
                .andExpect(jsonPath("$.premio.monedas").value(30));
        assertEquals("RECHAZADO", filaSesion(clave.toString())[0]);
        assertTrue(registros(leer).isEmpty());
        assertEquals(saldoAntes, saldoSinFocus(a));
        assertEquals(30, monedasDeSesiones(a), "el premio de la sesión, que se confirmó en el alta, se queda");
    }

    // ── Idempotencia ─────────────────────────────────────────────

    @Test
    void reintento_conLaMismaClave_noDuplicaMinutosNiMonedas() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        String cuerpo = sesion25(clave, leer);

        String primera = enviar(a, cuerpo).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        int saldoTrasLaPrimera = saldo(a);
        int xpTrasLaPrimera = xp(a);

        String reintento = enviar(a, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesion.id").value(idDe(primera)))
                .andExpect(jsonPath("$.aporte.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.aporte.minutos").value(25))
                .andReturn().getResponse().getContentAsString();
        // El reintento devuelve el resultado original, que es lo que la app
        // enseña si la primera respuesta se perdió por el camino.
        assertEquals(aporteDe(primera), aporteDe(reintento), "el mismo aporte que la primera vez");

        assertEquals(1, registros(leer).size(), "un solo registro");
        assertEquals(saldoTrasLaPrimera, saldo(a), "ni una moneda más");
        assertEquals(xpTrasLaPrimera, xp(a));
        assertEquals(1, sesionesDe(a));
    }

    /** El objeto "aporte" de una respuesta, tal cual. */
    private static String aporteDe(String respuesta) {
        return respuesta.substring(respuesta.indexOf("\"aporte\":"));
    }

    /**
     * Monedas, XP y logros del reintento son los guardados; el progreso del
     * día se lee en el momento: si desde entonces se ha sumado algo desde el
     * panel, el reintento lo dice.
     */
    @Test
    void reintento_devuelveLoPagadoOriginal_yElProgresoDelDiaDeAhora() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        String cuerpo = sesion25(clave, leer);
        enviar(a, cuerpo).andExpect(status().isCreated())
                .andExpect(jsonPath("$.aporte.monedas").value(20))
                .andExpect(jsonPath("$.aporte.xp").value(4))
                .andExpect(jsonPath("$.aporte.logros[0]").value("PRIMEROS_PASOS"));
        tx.execute(estado -> registroService.completar(habitoService.buscarPorId(leer),
                new Solicitud(null, VIERNES, "5", UUID.randomUUID(), OrigenRegistro.MANUAL)));

        enviar(a, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aporte.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.aporte.monedas").value(20))
                .andExpect(jsonPath("$.aporte.xp").value(4))
                .andExpect(jsonPath("$.aporte.logros", hasSize(1)))
                .andExpect(jsonPath("$.aporte.logros[0]").value("PRIMEROS_PASOS"))
                .andExpect(jsonPath("$.aporte.minutosDia").value(30))
                .andExpect(jsonPath("$.aporte.minutosObjetivo").value(30))
                .andExpect(jsonPath("$.aporte.diaHecho").value(true));
    }

    @Test
    void reintento_deUnRechazo_sinProgresoNiPremios() throws Exception {
        int a = usuario();
        int pausado = habito(a, "Leer", minutosDiario(30), false);
        String cuerpo = sesion25(UUID.randomUUID().toString(), pausado);
        enviar(a, cuerpo).andExpect(status().isCreated());
        enviar(a, cuerpo).andExpect(status().isOk())
                .andExpect(jsonPath("$.aporte.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.aporte.motivo").value("aporte.habito.pausado"))
                .andExpect(jsonPath("$.aporte.monedas").value(0))
                .andExpect(jsonPath("$.aporte.minutosDia").isEmpty())
                .andExpect(jsonPath("$.aporte.logros").isEmpty());
    }

    private static int idDe(String respuesta) {
        String trozo = respuesta.substring(respuesta.indexOf("\"id\":") + 5);
        return Integer.parseInt(trozo.substring(0, trozo.indexOf(',')));
    }

    /**
     * El proceso murió entre el aporte y el paso 3: el registro está en el
     * hábito pero la sesión sigue PENDIENTE. El reintento lo completa con
     * REPETIDO, sin sumar ni pagar otra vez.
     */
    @Test
    void sesionPendiente_elReintentoLaCompleta_sinSumarOtraVez() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        String cuerpo = sesion25(clave, leer);
        enviar(a, cuerpo).andExpect(status().isCreated());
        tx.executeWithoutResult(estado -> em.createNativeQuery(
                        "UPDATE sesion_enfoque SET aporte_estado = 'PENDIENTE', minutos_aportados = 0"
                                + " WHERE clave = CAST(:c AS UUID)")
                .setParameter("c", clave).executeUpdate());
        int saldoAntes = saldo(a);

        enviar(a, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aporte.estado").value("REPETIDO"))
                .andExpect(jsonPath("$.aporte.minutos").value(25))
                .andExpect(jsonPath("$.aporte.monedas").value(0))
                .andExpect(jsonPath("$.aporte.minutosDia").value(25));

        Object[] fila = filaSesion(clave);
        assertEquals("REPETIDO", fila[0]);
        assertEquals(25, ((Number) fila[2]).intValue());
        assertEquals(1, registros(leer).size());
        assertEquals(saldoAntes, saldo(a));
    }

    // ── A la vez ──────────────────────────────────────────────────

    /** Lo que responde una petición: el código y el cuerpo. */
    private record Respuesta(int estado, String cuerpo) {}

    /** Dos envíos a la vez (cada uno en su hilo, salen juntos) y sus respuestas. */
    private List<Respuesta> aLaVez(int usuarioId, String cuerpoA, String cuerpoB) throws Exception {
        java.util.concurrent.ExecutorService hilos = java.util.concurrent.Executors.newFixedThreadPool(2);
        try {
            java.util.concurrent.CountDownLatch salida = new java.util.concurrent.CountDownLatch(1);
            List<java.util.concurrent.Future<Respuesta>> futuros = new ArrayList<>();
            for (String cuerpo : List.of(cuerpoA, cuerpoB)) {
                futuros.add(hilos.submit(() -> {
                    salida.await();
                    var r = enviar(usuarioId, cuerpo).andReturn().getResponse();
                    return new Respuesta(r.getStatus(), r.getContentAsString());
                }));
            }
            salida.countDown();
            List<Respuesta> respuestas = new ArrayList<>();
            for (var futuro : futuros) {
                respuestas.add(futuro.get(30, java.util.concurrent.TimeUnit.SECONDS));
            }
            return respuestas;
        } finally {
            hilos.shutdownNow();
        }
    }

    private static List<Integer> estados(List<Respuesta> respuestas) {
        return respuestas.stream().map(Respuesta::estado).sorted().toList();
    }

    /**
     * La misma sesión enviada dos veces a la vez (la cola sin conexión y un
     * reintento): una se crea (201), la otra la encuentra (200), y el hábito
     * recibe un solo registro y se paga una sola vez.
     */
    @Test
    void laMismaClaveALaVez_una201YOtra200_unSoloRegistro_unSoloPago() throws Exception {
        for (int vuelta = 0; vuelta < 5; vuelta++) {
            int a = usuario();
            int leer = habito(a, "Leer", minutosDiario(30), true);
            String clave = UUID.randomUUID().toString();
            String cuerpo = sesion25(clave, leer);

            List<Respuesta> respuestas = aLaVez(a, cuerpo, cuerpo);

            assertEquals(List.of(200, 201), estados(respuestas), "vuelta " + vuelta + ": " + respuestas);
            assertEquals(1, registros(leer).size(), "vuelta " + vuelta + ": un solo registro");
            assertEquals(List.of("20 HABITO_COMPLETADO"),
                    movimientos(a).stream().filter(m -> m.endsWith("HABITO_COMPLETADO")).toList(),
                    "vuelta " + vuelta + ": un solo pago del hábito");
            assertEquals(1, sesionesDe(a));
            Object[] fila = filaSesion(clave);
            assertEquals("ACEPTADO", fila[0], "vuelta " + vuelta + ": queda apuntado el que pagó");
            assertEquals(25, ((Number) fila[2]).intValue());
        }
    }

    /** Dos sesiones distintas que se solapan, a la vez: una se guarda y la otra es 409. */
    @Test
    void dosSesionesQueSeSolapanALaVez_una201YOtra409() throws Exception {
        for (int vuelta = 0; vuelta < 5; vuelta++) {
            int a = usuario();
            Instant inicio = AHORA.minusSeconds(3600);
            String una = cuerpo(UUID.randomUUID().toString(), inicio, 1500, 25, "COMPLETADA", null);
            String otra = cuerpo(UUID.randomUUID().toString(), inicio.plusSeconds(300), 1500, 25, "COMPLETADA", null);

            List<Respuesta> respuestas = aLaVez(a, una, otra);

            assertEquals(List.of(201, 409), estados(respuestas), "vuelta " + vuelta + ": " + respuestas);
            assertEquals(1, sesionesDe(a), "vuelta " + vuelta);
        }
    }

    @Test
    void laMismaClave_conOtroCuerpo_es409_yNoCambiaNada() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        enviar(a, sesion25(clave, leer)).andExpect(status().isCreated());

        enviar(a, cuerpo(clave, AHORA.minusSeconds(2100), 1560, 25, "COMPLETADA", leer))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value("Ya hay una sesión distinta guardada con esa clave."));
        enviar(a, sesion25(clave, null)).andExpect(status().isConflict());

        // Otra cuenta con la misma clave, tampoco.
        int b = usuario();
        enviar(b, sesion25(clave, null)).andExpect(status().isConflict());

        assertEquals(1, registros(leer).size());
        assertEquals(25, registros(leer).get(0).getCantidad());
        assertEquals(1, sesionesDe(a));
        assertEquals(0, sesionesDe(b));
    }

    @Test
    void solape_conOtraSesion_es409() throws Exception {
        int a = usuario();
        Instant inicio = AHORA.minusSeconds(3600);
        enviar(a, cuerpo(UUID.randomUUID().toString(), inicio, 1500, 25, "COMPLETADA", null))
                .andExpect(status().isCreated());

        enviar(a, cuerpo(UUID.randomUUID().toString(), inicio.plusSeconds(600), 900, 15, "TERMINADA", null))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("se solapa")));
        enviar(a, cuerpo(UUID.randomUUID().toString(), inicio.minusSeconds(300), 600, 10, "TERMINADA", null))
                .andExpect(status().isConflict());
        // Justo al terminar la anterior, no se solapa.
        enviar(a, cuerpo(UUID.randomUUID().toString(), inicio.plusSeconds(1500), 600, 10, "TERMINADA", null))
                .andExpect(status().isCreated());
        // Las sesiones de otra cuenta no cuentan.
        int b = usuario();
        enviar(b, cuerpo(UUID.randomUUID().toString(), inicio, 1500, 25, "COMPLETADA", null))
                .andExpect(status().isCreated());
        assertEquals(2, sesionesDe(a));
    }

    // ── Validaciones ─────────────────────────────────────────────

    @Test
    void validaciones_400ConTexto_yNoGuardanNada() throws Exception {
        int a = usuario();
        String clave = UUID.randomUUID().toString();
        Instant inicio = AHORA.minusSeconds(1800);
        List<String> malos = List.of(
                cuerpo("no-es-una-clave", inicio, 1500, 25, "COMPLETADA", null),
                cuerpo(clave, inicio, 1500, 25, "ABANDONADA", null),
                cuerpo(clave, inicio, 1500, 12, "COMPLETADA", null),
                cuerpo(clave, inicio, 1500, 5, "COMPLETADA", null),
                cuerpo(clave, inicio, 1500, 125, "COMPLETADA", null),
                cuerpo(clave, inicio, 599, 25, "TERMINADA", null),
                cuerpo(clave, inicio, 1561, 25, "COMPLETADA", null),
                cuerpo(clave, AHORA.minusSeconds(8000), 7261, 120, "COMPLETADA", null),
                cuerpo(clave, AHORA.minusSeconds(1500 - 121), 1500, 25, "COMPLETADA", null),
                cuerpo(clave, AHORA.minus(java.time.Duration.ofDays(7)).minusSeconds(1), 1500, 25, "COMPLETADA", null),
                "{\"clave\":\"" + clave + "\",\"inicioUtc\":\"ayer\",\"duracionSeg\":1500,"
                        + "\"duracionObjetivoMin\":25,\"estado\":\"COMPLETADA\"}",
                "{\"clave\":\"" + clave + "\",\"inicioUtc\":\"" + inicio + "\",\"duracionSeg\":1500,"
                        + "\"duracionObjetivoMin\":25,\"estado\":\"COMPLETADA\",\"destino\":{\"tipo\":\"habito\"}}");
        for (String malo : malos) {
            enviar(a, malo).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").isNotEmpty());
        }
        assertEquals(0, sesionesDe(a), "nada de eso guarda una sesión");
        enviar(a, cuerpo(clave, inicio, 1500, 12, "COMPLETADA", null))
                .andExpect(jsonPath("$.mensaje").value("La duración elegida debe estar entre 10 y 120 minutos, de 5 en 5."));
        enviar(a, cuerpo(clave, inicio, 599, 25, "TERMINADA", null))
                .andExpect(jsonPath("$.mensaje").value("Una sesión que cuenta dura al menos 10 minutos."));

        // Los bordes que sí valen: 600 s terminada, PERDIDA de 0 s, 2 min de adelanto.
        enviar(a, cuerpo(UUID.randomUUID().toString(), AHORA.minusSeconds(6000), 600, 25, "TERMINADA", null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sesion.minutos").value(10));
        enviar(a, cuerpo(UUID.randomUUID().toString(), AHORA.minusSeconds(5000), 0, 25, "PERDIDA", null))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.sesion.minutos").value(0));
        enviar(a, cuerpo(UUID.randomUUID().toString(), AHORA.minusSeconds(1500 - 120), 1500, 25, "COMPLETADA", null))
                .andExpect(status().isCreated());
    }

    // ── Fallos técnicos y peticiones mal formadas ────────────────

    /**
     * Un fallo técnico al llevar los minutos: 503, la sesión queda guardada y
     * PENDIENTE, el hábito no recibe nada. El reintento con la misma clave,
     * ya sin fallo, la completa (200).
     */
    @Test
    void falloTecnicoDelDestino_503_laSesionQuedaPendiente_yElReintentoLaCompleta() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        String cuerpo = sesion25(clave, leer);
        doThrow(new org.springframework.dao.DataAccessResourceFailureException("la base de datos se ha ido"))
                .when(destinoHabitos).aportar(any(), any(), anyInt(), any(), any(), any());

        enviar(a, cuerpo)
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("Ha habido un problema temporal. Inténtalo de nuevo en un momento."));
        assertEquals("PENDIENTE", filaSesion(clave)[0]);
        assertTrue(registros(leer).isEmpty());

        doCallRealMethod().when(destinoHabitos).aportar(any(), any(), anyInt(), any(), any(), any());
        enviar(a, cuerpo)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aporte.estado").value("ACEPTADO"))
                .andExpect(jsonPath("$.aporte.minutos").value(25));
        assertEquals("ACEPTADO", filaSesion(clave)[0]);
        assertEquals(1, registros(leer).size());
    }

    @Test
    void unErrorInesperadoDelDestino_tambienEs503() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        doThrow(new IllegalArgumentException("inesperado"))
                .when(destinoHabitos).aportar(any(), any(), anyInt(), any(), any(), any());
        enviar(a, sesion25(clave, leer)).andExpect(status().isServiceUnavailable());
        assertEquals("PENDIENTE", filaSesion(clave)[0]);
    }

    @Test
    void peticionMalFormada_400ConTexto() throws Exception {
        int a = usuario();
        enviar(a, "{").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La petición no tiene el formato esperado."));
        enviar(a, sesion25(UUID.randomUUID().toString(), null).replace("1500", "\"mucho\""))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La petición no tiene el formato esperado."));
        mockMvc.perform(get("/api/enfoque/sesiones?pagina=abc").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("La petición no tiene el formato esperado."));
        mockMvc.perform(get("/api/enfoque/sesiones?pagina=10001").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/enfoque/sesiones?pagina=10000").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk());
        assertEquals(0, sesionesDe(a));
    }

    @Test
    void elTipoDeDestinoQueNadieDeclara_seRechaza() throws Exception {
        int a = usuario();
        String clave = UUID.randomUUID().toString();
        String cuerpo = sesion25(clave, 1).replace("\"habito\"", "\"libro\"");
        enviar(a, cuerpo)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.aporte.estado").value("RECHAZADO"))
                .andExpect(jsonPath("$.aporte.motivo").value("enfoque.aporte.destino.desconocido"));
        assertEquals("RECHAZADO", filaSesion(clave)[0]);
    }

    @Test
    void sinToken_401() throws Exception {
        mockMvc.perform(post("/api/enfoque/sesiones").contentType(MediaType.APPLICATION_JSON)
                .content(sesion25(UUID.randomUUID().toString(), null))).andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/enfoque/destinos")).andExpect(status().isUnauthorized());
    }

    // ── Destinos, resumen e historial ────────────────────────────

    @Test
    void destinos_soloLosHabitosDeMinutosActivos_conLoQueLlevanHoy() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        habito(a, "Meditar", vecesDiario(), true);
        habito(a, "Correr", minutosDiario(20), false);
        enviar(a, sesion25(UUID.randomUUID().toString(), leer)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/enfoque/destinos").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.destinos", hasSize(1)))
                .andExpect(jsonPath("$.destinos[0].tipo").value("habito"))
                .andExpect(jsonPath("$.destinos[0].id").value(leer))
                .andExpect(jsonPath("$.destinos[0].nombre").value("Leer"))
                .andExpect(jsonPath("$.destinos[0].minutosDia").value(25))
                .andExpect(jsonPath("$.destinos[0].minutosObjetivo").value(30));
    }

    @Test
    void resumen_minutosPorDia_totales_yRacha() throws Exception {
        int a = usuario();
        // Jueves: una de 25 y una perdida. Viernes: una de 25 sin destino.
        ahora(JUEVES.atTime(18, 0).atZone(MADRID).toInstant(), JUEVES);
        enviar(a, cuerpo(UUID.randomUUID().toString(), JUEVES.atTime(9, 0).atZone(MADRID).toInstant(), 1500, 25,
                "COMPLETADA", null)).andExpect(status().isCreated());
        enviar(a, cuerpo(UUID.randomUUID().toString(), JUEVES.atTime(11, 0).atZone(MADRID).toInstant(), 400, 25,
                "PERDIDA", null)).andExpect(status().isCreated());
        ahora(AHORA, VIERNES);
        enviar(a, sesion25(UUID.randomUUID().toString(), null)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/enfoque/resumen?desde=2026-10-05&hasta=2026-10-11")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dias", hasSize(7)))
                .andExpect(jsonPath("$.dias[0].dia").value("2026-10-05"))
                .andExpect(jsonPath("$.dias[0].minutos").value(0))
                .andExpect(jsonPath("$.dias[3].dia").value("2026-10-08"))
                .andExpect(jsonPath("$.dias[3].minutos").value(25))
                .andExpect(jsonPath("$.dias[3].sesiones").value(1))
                .andExpect(jsonPath("$.dias[4].minutos").value(25))
                .andExpect(jsonPath("$.totalMinutos").value(50))
                .andExpect(jsonPath("$.totalSesiones").value(2))
                .andExpect(jsonPath("$.rachaActual").value(2));

        mockMvc.perform(get("/api/enfoque/resumen?desde=2026-08-01&hasta=2026-10-11")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/enfoque/resumen?desde=ayer&hasta=2026-10-11")
                        .header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void historial_masRecientePrimero_yPaginado() throws Exception {
        int a = usuario();
        for (int i = 0; i < 3; i++) {
            enviar(a, cuerpo(UUID.randomUUID().toString(), AHORA.minusSeconds(7200 - i * 1800L), 900, 15,
                    "TERMINADA", null)).andExpect(status().isCreated());
        }
        mockMvc.perform(get("/api/enfoque/sesiones?pagina=0&tamano=2").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sesiones", hasSize(2)))
                .andExpect(jsonPath("$.sesiones[0].inicioUtc").value(AHORA.minusSeconds(3600).toString()))
                .andExpect(jsonPath("$.sesiones[0].aporte.estado").value("SIN_DESTINO"))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.hayMas").value(true));
        mockMvc.perform(get("/api/enfoque/sesiones?pagina=1&tamano=2").header("Authorization", "Bearer " + token(a)))
                .andExpect(jsonPath("$.sesiones", hasSize(1)))
                .andExpect(jsonPath("$.hayMas").value(false));
        mockMvc.perform(get("/api/enfoque/sesiones?tamano=101").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isBadRequest());
    }

    // ── Baja y exportación ───────────────────────────────────────

    @Test
    void laBajaDeLaCuenta_borraLasSesiones_conLaFkReal() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        enviar(a, sesion25(UUID.randomUUID().toString(), leer)).andExpect(status().isCreated());
        enviar(a, cuerpo(UUID.randomUUID().toString(), AHORA.minusSeconds(9000), 600, 10, "TERMINADA", null))
                .andExpect(status().isCreated());
        assertEquals(2, sesionesDe(a));

        usuarioService.eliminarCuenta(a);
        usuarios.remove(Integer.valueOf(a));

        assertEquals(0, sesionesDe(a));
        assertNull(tx.execute(estado -> em.find(Usuario.class, a)));
    }

    @Test
    void laExportacion_llevaLasSesiones_sinLaClave() throws Exception {
        int a = usuario();
        int leer = habito(a, "Leer", minutosDiario(30), true);
        String clave = UUID.randomUUID().toString();
        enviar(a, sesion25(clave, leer)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/usuarios/" + a + "/exportar").header("Authorization", "Bearer " + token(a)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.enfoque.sesiones", hasSize(1)))
                .andExpect(jsonPath("$.enfoque.sesiones[0].estado").value("COMPLETADA"))
                .andExpect(jsonPath("$.enfoque.sesiones[0].dia").value("2026-10-09"))
                .andExpect(jsonPath("$.enfoque.sesiones[0].minutos").value(25))
                .andExpect(jsonPath("$.enfoque.sesiones[0].destinoTipo").value("habito"))
                .andExpect(jsonPath("$.enfoque.sesiones[0].destinoId").value(leer))
                .andExpect(jsonPath("$.enfoque.sesiones[0].aporteEstado").value("ACEPTADO"))
                .andExpect(jsonPath("$.enfoque.sesiones[0].minutosAportados").value(25))
                .andExpect(jsonPath("$.enfoque.sesiones[0].monedasAportadas").value(20))
                .andExpect(jsonPath("$.enfoque.sesiones[0].xpAportada").value(4))
                .andExpect(jsonPath("$.enfoque.sesiones[0].logrosAportados[0]").value("PRIMEROS_PASOS"))
                .andExpect(jsonPath("$.enfoque.sesiones[0].premioMonedas").value(30))
                .andExpect(jsonPath("$.enfoque.sesiones[0].premioXp").value(5))
                .andExpect(jsonPath("$.enfoque.sesiones[0].premioLogros", hasSize(2)))
                .andExpect(jsonPath("$.enfoque.sesiones[0]", not(hasKey("clave"))));
    }
}
