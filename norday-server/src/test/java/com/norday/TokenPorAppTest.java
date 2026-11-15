package com.norday;

import com.norday.core.model.AppNorday;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.DispositivoService;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.service.NotificacionScheduler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Cada app del ecosistema registra su propio token de notificaciones: FCM da
 * uno por instancia de app, así que Habits y Enfoque en el mismo móvil tienen
 * dos tokens distintos. Con un solo token por usuario, la segunda app pisaba
 * a la primera y los recordatorios de hábitos acababan en Enfoque.
 *
 * Contra la aplicación ensamblada, con el endpoint, la base de datos y el
 * barrido de verdad. Solo el envío a Firebase es falso: es lo que se observa.
 * Se cambia a mano en el barrido, y no con @MockitoBean, para no levantar un
 * segundo contexto Spring solo para esta clase.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class TokenPorAppTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    /** Instante fijo del barrido: martes 10-nov-2026, 10:02 en Madrid. */
    private static final Instant AHORA = Instant.parse("2026-11-10T09:02:00Z");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private NotificacionScheduler notificacionScheduler;

    @Autowired
    private DispositivoService dispositivoService;

    @Autowired
    private UsuarioService usuarioService;

    private NotificacionService notificacionService;

    private NotificacionService notificacionServiceReal;

    @BeforeEach
    void envioFalso() {
        notificacionServiceReal = (NotificacionService) ReflectionTestUtils.getField(
                notificacionScheduler, "notificacionService");
        notificacionService = mock(NotificacionService.class);
        ReflectionTestUtils.setField(notificacionScheduler, "notificacionService", notificacionService);
    }

    @AfterEach
    void envioReal() {
        ReflectionTestUtils.setField(notificacionScheduler, "notificacionService", notificacionServiceReal);
    }

    @PersistenceContext
    private EntityManager em;

    private Usuario usuarioDePrueba() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Token");
        usuario.setUsername("test-token-" + System.nanoTime());
        usuario.setEmail("test-token-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria(MADRID.getId());
        em.persist(usuario);
        em.flush();
        return usuario;
    }

    /** PUT del token como lo hace el core; appId null = sin cabecera, como la APK instalada. */
    private MockHttpServletRequestBuilder registrar(Usuario usuario, String appId, String token) {
        MockHttpServletRequestBuilder peticion = put("/api/usuarios/" + usuario.getUsuarioId() + "/fcm-token")
                .header("Authorization", "Bearer " + jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail()))
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"fcmToken\":\"" + token + "\"}");
        if (appId != null) {
            peticion = peticion.header("X-Norday-App", appId);
        }
        return peticion;
    }

    /**
     * Un hábito con el recordatorio en la ventana de 5 minutos de AHORA en
     * Madrid, empezado ese día y sin hacer: el barrido tiene que avisar.
     */
    private void habitoConRecordatorioAhora(Usuario usuario) {
        LocalTime enSuReloj = LocalTime.ofInstant(AHORA, MADRID).withSecond(0).withNano(0);
        LocalTime enVentana = enSuReloj.withMinute((enSuReloj.getMinute() / 5) * 5);
        Habito habito = new Habito("Leer", "Descripción", Frecuencia.DIARIO, 1,
                em.find(Usuario.class, usuario.getUsuarioId()), null, LocalDate.ofInstant(AHORA, MADRID));
        habito.setRecordatorioActivo(true);
        habito.setRecordatorioHora(enVentana);
        em.persist(habito);
        em.flush();
    }

    private String tokenEnUsuario(Usuario usuario) {
        em.flush();
        em.clear();
        return em.find(Usuario.class, usuario.getUsuarioId()).getFcmToken();
    }

    @Test
    void registrarElDeEnfoque_noPisaElDeHabitos_yElRecordatorioVaAlDeHabitos() throws Exception {
        Usuario usuario = usuarioDePrueba();

        mockMvc.perform(registrar(usuario, "habitos", "token-habitos")).andExpect(status().isOk());
        mockMvc.perform(registrar(usuario, "enfoque", "token-enfoque")).andExpect(status().isOk());

        habitoConRecordatorioAhora(usuario);

        notificacionScheduler.enviarRecordatorios(AHORA);

        verify(notificacionService).enviarNotificacion(eq("token-habitos"), anyString(), anyString());
        verify(notificacionService, never()).enviarNotificacion(eq("token-enfoque"), anyString(), anyString());

        // Durante la transición, usuario.fcm_token sigue siendo el de Habits.
        assertEquals("token-habitos", tokenEnUsuario(usuario));
    }

    @Test
    void appDesconocida_devuelve400_yNoTocaElTokenDeHabitos() throws Exception {
        Usuario usuario = usuarioDePrueba();

        mockMvc.perform(registrar(usuario, null, "token-habitos")).andExpect(status().isOk());
        mockMvc.perform(registrar(usuario, "otra-app", "token-otra")).andExpect(status().isBadRequest());

        assertEquals("token-habitos", tokenEnUsuario(usuario));
    }

    @Test
    void sinCabecera_esHabits_comoLaApkInstalada() throws Exception {
        Usuario usuario = usuarioDePrueba();

        mockMvc.perform(registrar(usuario, null, "token-apk")).andExpect(status().isOk());

        assertEquals("token-apk", dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.HABITOS));
        assertEquals("token-apk", tokenEnUsuario(usuario));
    }

    @Test
    void cabeceraVacia_esHabits() throws Exception {
        Usuario usuario = usuarioDePrueba();

        mockMvc.perform(registrar(usuario, "  ", "token-vacia")).andExpect(status().isOk());

        assertEquals("token-vacia", dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.HABITOS));
    }

    @Test
    void registrarDeNuevo_sustituyeElDeEsaApp_sinDuplicarFila() throws Exception {
        Usuario usuario = usuarioDePrueba();

        mockMvc.perform(registrar(usuario, "enfoque", "token-viejo")).andExpect(status().isOk());
        mockMvc.perform(registrar(usuario, "enfoque", "token-nuevo")).andExpect(status().isOk());

        assertEquals("token-nuevo", dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.ENFOQUE));
        assertEquals(1L, filasDe(usuario));
        // Enfoque no escribe la columna de transición, que es solo de Habits.
        assertNull(tokenEnUsuario(usuario));
    }

    @Test
    void tokenVacio_dejaAEsaAppSinToken_yNoTocaLaOtra() throws Exception {
        Usuario usuario = usuarioDePrueba();
        mockMvc.perform(registrar(usuario, "habitos", "token-habitos")).andExpect(status().isOk());
        mockMvc.perform(registrar(usuario, "enfoque", "token-enfoque")).andExpect(status().isOk());

        mockMvc.perform(registrar(usuario, "enfoque", "")).andExpect(status().isOk());

        assertNull(dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.ENFOQUE));
        assertEquals("token-habitos", dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.HABITOS));
    }

    @Test
    void olvidarUnTokenRechazado_soloBorraSiSigueSiendoEse() {
        Usuario usuario = usuarioDePrueba();
        int id = usuario.getUsuarioId();
        dispositivoService.registrarToken(id, AppNorday.HABITOS, "token-nuevo");

        // FCM rechaza el viejo cuando la app ya ha registrado otro: se queda el nuevo.
        dispositivoService.olvidarToken(id, AppNorday.HABITOS, "token-viejo");
        assertEquals("token-nuevo", dispositivoService.tokenDe(id, AppNorday.HABITOS));
        assertEquals("token-nuevo", tokenEnUsuario(usuario));

        // FCM rechaza el vigente: se borra de la tabla y de la columna de transición.
        dispositivoService.olvidarToken(id, AppNorday.HABITOS, "token-nuevo");
        assertNull(dispositivoService.tokenDe(id, AppNorday.HABITOS));
        assertNull(tokenEnUsuario(usuario));
    }

    /**
     * Con la clave ajena de verdad: sin su limpiador, borrar el usuario
     * revienta por FK_usuario_dispositivo_usuario. Recorre eliminarCuenta
     * entero, con todos los limpiadores, como el endpoint.
     */
    @Test
    void eliminarCuentaConTokensDeDosApps_noRevientaPorFk() {
        Usuario usuario = usuarioDePrueba();
        int id = usuario.getUsuarioId();
        dispositivoService.registrarToken(id, AppNorday.HABITOS, "token-habitos");
        dispositivoService.registrarToken(id, AppNorday.ENFOQUE, "token-enfoque");
        em.flush();
        assertEquals(2L, filasDe(usuario));
        em.clear();

        usuarioService.eliminarCuenta(id);
        em.flush();

        assertNull(em.find(Usuario.class, id));
        assertEquals(0L, filasDe(usuario));
    }

    private long filasDe(Usuario usuario) {
        return em.createQuery(
                        "SELECT count(d) FROM UsuarioDispositivo d WHERE d.usuario.usuarioId = :id", Long.class)
                .setParameter("id", usuario.getUsuarioId())
                .getSingleResult();
    }

    /**
     * Lo que deja el jar anterior a V24 si se vuelve a él: el token en
     * usuario.fcm_token y ninguna fila en usuario_dispositivo.
     */
    private void tokenSoloEnLaColumna(Usuario usuario, String token) {
        Usuario gestionado = em.find(Usuario.class, usuario.getUsuarioId());
        gestionado.setFcmToken(token);
        em.flush();
        em.clear();
    }

    @Test
    void sinFilaDeHabits_elRecordatorioUsaLaColumnaDeTransicion() {
        Usuario usuario = usuarioDePrueba();
        tokenSoloEnLaColumna(usuario, "token-columna");
        habitoConRecordatorioAhora(usuario);

        notificacionScheduler.enviarRecordatorios(AHORA);

        verify(notificacionService).enviarNotificacion(eq("token-columna"), anyString(), anyString());
        assertEquals("token-columna", dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.HABITOS));
    }

    @Test
    void laColumnaDeTransicion_soloValeParaHabits() {
        Usuario usuario = usuarioDePrueba();
        tokenSoloEnLaColumna(usuario, "token-columna");

        assertNull(dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.ENFOQUE));
        assertEquals(java.util.Map.of(), dispositivoService.tokensDe(
                java.util.List.of(usuario.getUsuarioId()), AppNorday.ENFOQUE));
    }

    @Test
    void conFilaDeHabits_mandaLaFila() {
        Usuario usuario = usuarioDePrueba();
        dispositivoService.registrarToken(usuario.getUsuarioId(), AppNorday.HABITOS, "token-fila");
        tokenSoloEnLaColumna(usuario, "token-otro");

        assertEquals("token-fila", dispositivoService.tokenDe(usuario.getUsuarioId(), AppNorday.HABITOS));
    }

    /**
     * A cierra sesión y B entra en el mismo móvil: la app manda el mismo
     * token, ahora para B. Los avisos de A no pueden seguir llegando a ese
     * móvil, que ya es de B.
     */
    @Test
    void elMismoTokenEnOtroUsuario_dejaDeSerDelAnterior_yNoLeLleganSusAvisos() throws Exception {
        Usuario a = usuarioDePrueba();
        Usuario b = usuarioDePrueba();
        mockMvc.perform(registrar(a, "habitos", "token-movil")).andExpect(status().isOk());
        mockMvc.perform(registrar(b, "habitos", "token-movil")).andExpect(status().isOk());
        habitoConRecordatorioAhora(a);

        notificacionScheduler.enviarRecordatorios(AHORA);

        verify(notificacionService, never()).enviarNotificacion(eq("token-movil"), anyString(), anyString());
        assertNull(dispositivoService.tokenDe(a.getUsuarioId(), AppNorday.HABITOS));
        assertNull(tokenEnUsuario(a));
        assertEquals("token-movil", dispositivoService.tokenDe(b.getUsuarioId(), AppNorday.HABITOS));
        assertEquals("token-movil", tokenEnUsuario(b));
    }

    @Test
    void elMismoTokenDeEnfoqueEnOtroUsuario_soloQuitaElDeEnfoqueDelAnterior() throws Exception {
        Usuario a = usuarioDePrueba();
        Usuario b = usuarioDePrueba();
        mockMvc.perform(registrar(a, "habitos", "token-habits-a")).andExpect(status().isOk());
        mockMvc.perform(registrar(a, "enfoque", "token-focus")).andExpect(status().isOk());

        mockMvc.perform(registrar(b, "enfoque", "token-focus")).andExpect(status().isOk());

        assertNull(dispositivoService.tokenDe(a.getUsuarioId(), AppNorday.ENFOQUE));
        assertEquals("token-habits-a", dispositivoService.tokenDe(a.getUsuarioId(), AppNorday.HABITOS));
        assertEquals("token-habits-a", tokenEnUsuario(a));
        assertEquals("token-focus", dispositivoService.tokenDe(b.getUsuarioId(), AppNorday.ENFOQUE));
    }
}
