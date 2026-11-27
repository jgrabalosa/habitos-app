package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.model.dto.HoyDTO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.HoyService;
import com.norday.habitos.service.RachaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Crear y editar hábitos de minutos de punta a punta, la regla de cambios
 * con unidad y minutos, anular un cambio programado, dos PUT a la vez, Hoy
 * mirando una semana futura y que /dashboard y /semana ya no existen.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe, que es por
 * donde leen el reloj HabitoService.actualizar y la anulación. Las versiones
 * se leen con SQL nativo sobre habito_objetivo.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class EditarMinutosTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final LocalDate VIERNES = LUNES.plusDays(4);
    private static final LocalDate SABADO = LUNES.plusDays(5);
    private static final LocalDate LUNES_SIGUIENTE = LUNES.plusWeeks(1);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private HabitoService habitoService;
    @Autowired private HoyService hoyService;
    @Autowired private IHabitoObjetivoDAO objetivoDAO;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private String token;

    private void unUsuario() {
        String email = "test-edmin-" + System.nanoTime() + "@example.com";
        usuario = new Usuario();
        usuario.setNombre("Test Editar Minutos");
        usuario.setUsername("test-edmin-" + System.nanoTime());
        usuario.setEmail(email);
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        token = jwtUtil.generateToken(usuario.getUsuarioId(), email);
        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    private Habito habito(Objetivo objetivo) {
        if (usuario == null) unUsuario();
        Habito habito = new Habito("Leer", null, objetivo.frecuencia(), objetivo.meta(), usuario, null, INICIO);
        habito.setUnidad(objetivo.unidad());
        habito.setMinutosObjetivo(objetivo.minutosObjetivo());
        habito.setDiasSemana(objetivo.diasSemana());
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, objetivo));
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        return habito;
    }

    private void registro(Habito habito, LocalDate fecha) {
        em.persist(new Registro(em.find(Habito.class, habito.getHabitoId()), true, null, fecha));
        em.flush();
    }

    private Habito leido(int id) {
        em.flush();
        em.clear();
        return em.find(Habito.class, id);
    }

    /** Las versiones como "desde unidad frecuencia meta minutos dias". */
    @SuppressWarnings("unchecked")
    private List<String> versiones(Habito habito) {
        em.flush();
        List<Object[]> filas = em.createNativeQuery(
                "SELECT desde, unidad, frecuencia, meta, minutos_objetivo, dias_semana FROM habito_objetivo "
                        + "WHERE habito_ref = " + habito.getHabitoId() + " ORDER BY desde").getResultList();
        return filas.stream().map(f -> f[0] + " " + f[1] + " " + f[2] + " " + f[3] + " " + f[4] + " " + f[5])
                .toList();
    }

    private ResultActions crear(String json) throws Exception {
        if (usuario == null) unUsuario();
        return mockMvc.perform(post("/api/habitos").header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions editar(Habito habito, String json) throws Exception {
        return mockMvc.perform(put("/api/habitos/" + habito.getHabitoId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private Habito elUnicoCreado() {
        em.flush();
        em.clear();
        return em.createQuery("SELECT h FROM Habito h WHERE h.propietario.usuarioId = :u", Habito.class)
                .setParameter("u", usuario.getUsuarioId()).getSingleResult();
    }

    private static String cuerpo(String frecuencia, int meta, String dias, String extra) {
        return "{\"nombre\":\"Leer\",\"frecuencia\":\"" + frecuencia + "\",\"meta\":" + meta
                + ",\"diasSemana\":" + (dias == null ? "null" : "\"" + dias + "\"")
                + ",\"recordatorioActivo\":true" + extra + "}";
    }

    private static Objetivo minutos(Frecuencia frecuencia, int meta, int minutos) {
        return new Objetivo(Unidad.MINUTOS, frecuencia, meta, minutos, null);
    }

    // ── Crear ────────────────────────────────────────────────────

    @Test
    void crear_minutosDiario_fuerzaLaMetaAUno() throws Exception {
        crear(cuerpo("DIARIO", 3, null, ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":30"))
                .andExpect(status().isCreated());
        Habito creado = elUnicoCreado();
        assertEquals(Unidad.MINUTOS, creado.getUnidad());
        assertEquals(30, creado.getMinutosObjetivo());
        assertEquals(1, creado.getMeta());
        assertEquals(List.of(creado.getFechaInicio() + " MINUTOS DIARIO 1 30 null"), versiones(creado));
    }

    @Test
    void crear_minutosSemanal_laMetaSonLosDias() throws Exception {
        crear(cuerpo("SEMANAL", 2, "1,3,5", ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":60"))
                .andExpect(status().isCreated());
        Habito creado = elUnicoCreado();
        assertEquals(3, creado.getMeta());
        assertEquals(60, creado.getMinutosObjetivo());
    }

    @Test
    void crear_minutosSinMinutosOFueraDeRango_esUnaPeticionMala() throws Exception {
        for (String extra : List.of(",\"unidad\":\"MINUTOS\"", ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":0",
                ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":601")) {
            crear(cuerpo("DIARIO", 1, null, extra)).andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(containsString("entre 1 y 600")));
        }
        em.flush();
        assertEquals(0L, em.createQuery("SELECT COUNT(h) FROM Habito h WHERE h.propietario.usuarioId = :u")
                .setParameter("u", usuario.getUsuarioId()).getSingleResult());
    }

    @Test
    void crear_sinUnidad_esDeVeces_yLosMinutosSeIgnoran() throws Exception {
        crear(cuerpo("DIARIO", 2, null, ",\"minutosObjetivo\":30")).andExpect(status().isCreated());
        Habito creado = elUnicoCreado();
        assertEquals(Unidad.VECES, creado.getUnidad());
        assertNull(creado.getMinutosObjetivo());
        assertEquals(2, creado.getMeta());
    }

    // ── Editar ───────────────────────────────────────────────────

    @Test
    void editar_conLaAppYaInstalada_noTocaLaUnidadNiLosMinutos() throws Exception {
        Habito leer = habito(minutos(Frecuencia.DIARIO, 1, 30));
        editar(leer, cuerpo("DIARIO", 1, null, "").replace("\"Leer\"", "\"Leer más\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaDesde").value(VIERNES.toString()));
        Habito tras = leido(leer.getHabitoId());
        assertEquals("Leer más", tras.getNombre());
        assertEquals(Unidad.MINUTOS, tras.getUnidad());
        assertEquals(30, tras.getMinutosObjetivo());
        assertEquals(1, versiones(tras).size(), "solo el nombre: ninguna versión nueva");
    }

    @Test
    void editar_soloElNombre_conDiasHeredadosEnOtroOrden_noCreaVersion() throws Exception {
        Habito llamar = habito(new Objetivo(Frecuencia.SEMANAL, 2, "4,2"));
        registro(llamar, MARTES);
        editar(llamar, cuerpo("SEMANAL", 2, "2,4", "")).andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaDesde").value(VIERNES.toString()));
        assertEquals(1, versiones(llamar).size());
    }

    @Test
    void editar_losMinutos_conRegistrosHoy_manana_sinEllos_hoyYAnclaElRitmo() throws Exception {
        Habito leer = habito(minutos(Frecuencia.DIARIO, 1, 30));
        registro(leer, VIERNES);
        editar(leer, cuerpo("DIARIO", 1, null, ",\"minutosObjetivo\":40"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaDesde").value(SABADO.toString()));
        assertEquals(30, leido(leer.getHabitoId()).getMinutosObjetivo(), "hasta mañana, los de hoy");
        assertEquals(List.of(INICIO + " MINUTOS DIARIO 1 30 null", SABADO + " MINUTOS DIARIO 1 40 null"),
                versiones(leer));

        Habito otro = habito(minutos(Frecuencia.DIARIO, 1, 30));
        editar(otro, cuerpo("DIARIO", 1, null, ",\"minutosObjetivo\":45"))
                .andExpect(jsonPath("$.aplicaDesde").value(VIERNES.toString()));
        Habito tras = leido(otro.getHabitoId());
        assertEquals(45, tras.getMinutosObjetivo());
        Ritmo ritmo = em.createQuery("SELECT r FROM Ritmo r WHERE r.habito = :h", Ritmo.class)
                .setParameter("h", tras).getSingleResult();
        assertEquals(VIERNES, ritmo.getCambio(), "cambiar los minutos ancla el ritmo en la fecha de efecto");
    }

    @Test
    void editar_deVecesAMinutos_esDelDia_yConParteSemanal_deLaSemana() throws Exception {
        Habito leer = habito(new Objetivo(Frecuencia.DIARIO, 3, null));
        registro(leer, VIERNES);
        editar(leer, cuerpo("DIARIO", 3, null, ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":30"))
                .andExpect(jsonPath("$.aplicaDesde").value(SABADO.toString()));
        assertEquals(SABADO + " MINUTOS DIARIO 1 30 null", versiones(leer).get(1));
        assertEquals(Unidad.VECES, leido(leer.getHabitoId()).getUnidad(), "hoy sigue de veces");

        Habito entrenar = habito(new Objetivo(Frecuencia.SEMANAL, 3, null));
        registro(entrenar, MARTES);
        editar(entrenar, cuerpo("SEMANAL", 4, null, ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":60"))
                .andExpect(jsonPath("$.aplicaDesde").value(LUNES_SIGUIENTE.toString()));
        assertEquals(LUNES_SIGUIENTE + " MINUTOS SEMANAL 4 60 null", versiones(entrenar).get(1));
    }

    @Test
    void editar_deMinutosAVeces_esUnConflicto_yFueraDeRango_unaPeticionMala() throws Exception {
        Habito leer = habito(minutos(Frecuencia.DIARIO, 1, 30));
        editar(leer, cuerpo("DIARIO", 1, null, ",\"unidad\":\"VECES\""))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("minutos")));
        editar(leer, cuerpo("DIARIO", 1, null, ",\"minutosObjetivo\":0")).andExpect(status().isBadRequest());
        Habito tras = leido(leer.getHabitoId());
        assertEquals(Unidad.MINUTOS, tras.getUnidad());
        assertEquals(30, tras.getMinutosObjetivo());
        assertEquals(1, versiones(tras).size());
    }

    @Test
    void detalle_elCambioProgramadoLlevaUnidadYMinutos() throws Exception {
        // El detalle lee el reloj real: el cambio se programa para mañana de verdad.
        LocalDate manana = LocalDate.now(ZoneId.of("Europe/Madrid")).plusDays(1);
        Habito leer = habito(minutos(Frecuencia.DIARIO, 1, 30));
        em.persist(new HabitoObjetivo(em.find(Habito.class, leer.getHabitoId()), manana,
                minutos(Frecuencia.DIARIO, 1, 40)));
        em.flush();
        em.clear();
        mockMvc.perform(get("/api/habitos/" + leer.getHabitoId() + "/detalle")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unidad").value("MINUTOS"))
                .andExpect(jsonPath("$.minutosObjetivo").value(30))
                .andExpect(jsonPath("$.objetivoProgramado.desde").value(manana.toString()))
                .andExpect(jsonPath("$.objetivoProgramado.unidad").value("MINUTOS"))
                .andExpect(jsonPath("$.objetivoProgramado.minutosObjetivo").value(40));
    }

    @Test
    void volverAlObjetivoDeHoyConUnidad_anulaElProgramado_sinUnidad_no() throws Exception {
        // De veces a minutos con registros hoy: queda programado para mañana.
        Habito leer = habito(new Objetivo(Frecuencia.DIARIO, 1, null));
        registro(leer, VIERNES);
        editar(leer, cuerpo("DIARIO", 1, null, ",\"unidad\":\"MINUTOS\",\"minutosObjetivo\":30"))
                .andExpect(jsonPath("$.aplicaDesde").value(SABADO.toString()));
        assertEquals(2, versiones(leer).size());

        // La app ya instalada (sin unidad) solo cambia el nombre: no anula.
        editar(leer, cuerpo("DIARIO", 1, null, "").replace("\"Leer\"", "\"Leer más\""))
                .andExpect(status().isOk());
        assertEquals(2, versiones(leer).size(), "sin unidad, el cambio programado sigue");

        // La app nueva vuelve al objetivo de hoy, con unidad: anula.
        editar(leer, cuerpo("DIARIO", 1, null, ",\"unidad\":\"VECES\""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.aplicaDesde").value(VIERNES.toString()));
        assertEquals(List.of(INICIO + " VECES DIARIO 1 null null"), versiones(leer));

        // Al día siguiente sigue de veces.
        doReturn(SABADO).when(rachaService).hoyDe(any(Habito.class));
        Habito manana = leido(leer.getHabitoId());
        habitoService.ponerObjetivoAlDia(manana);
        Habito tras = leido(leer.getHabitoId());
        assertEquals(Unidad.VECES, tras.getUnidad());
        assertNull(tras.getMinutosObjetivo());
    }

    // ── Anular el cambio programado ──────────────────────────────

    @Test
    void anular_borraLasVersionesFuturas_yDejaLaCopiaComoEsta() throws Exception {
        Habito leer = habito(minutos(Frecuencia.DIARIO, 1, 30));
        registro(leer, VIERNES);
        editar(leer, cuerpo("DIARIO", 1, null, ",\"minutosObjetivo\":40"));
        assertEquals(2, versiones(leer).size());

        mockMvc.perform(delete("/api/habitos/" + leer.getHabitoId() + "/objetivo-programado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").exists());
        assertEquals(List.of(INICIO + " MINUTOS DIARIO 1 30 null"), versiones(leer));
        assertEquals(30, leido(leer.getHabitoId()).getMinutosObjetivo());

        // Ajeno: prohibido; inexistente: no encontrado.
        Usuario otro = new Usuario();
        otro.setNombre("Otro");
        otro.setUsername("test-edmin-otro-" + System.nanoTime());
        otro.setEmail("test-edmin-otro-" + System.nanoTime() + "@example.com");
        otro.setContrasena("contrasenaDePrueba");
        otro.setProveedorAuth("LOCAL");
        otro.setFechaRegistro(LocalDateTime.now());
        otro.setIdioma("es");
        otro.setZonaHoraria("Europe/Madrid");
        em.persist(otro);
        em.flush();
        String tokenAjeno = jwtUtil.generateToken(otro.getUsuarioId(), otro.getEmail());
        mockMvc.perform(delete("/api/habitos/" + leer.getHabitoId() + "/objetivo-programado")
                        .header("Authorization", "Bearer " + tokenAjeno))
                .andExpect(status().isForbidden());
        mockMvc.perform(delete("/api/habitos/999999999/objetivo-programado")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    // ── Dos PUT a la vez ─────────────────────────────────────────

    @Test
    void dosVersionesConLaMismaFecha_laSegundaNoSeEscribe_yLaTransaccionSigueViva() {
        Habito leer = habito(minutos(Frecuencia.DIARIO, 1, 30));
        Habito leido = leido(leer.getHabitoId());
        assertTrue(objetivoDAO.insertarSiLibre(new HabitoObjetivo(leido, SABADO, minutos(Frecuencia.DIARIO, 1, 40))));
        assertFalse(objetivoDAO.insertarSiLibre(new HabitoObjetivo(leido, SABADO, minutos(Frecuencia.DIARIO, 1, 50))));
        assertEquals(List.of(INICIO + " MINUTOS DIARIO 1 30 null", SABADO + " MINUTOS DIARIO 1 40 null"),
                versiones(leer));
    }

    // ── /dashboard y /semana, retirados ──────────────────────────

    @Test
    void dashboardYSemana_estanRetirados_yNoDevuelvenDatos() throws Exception {
        habito(new Objetivo(Frecuencia.DIARIO, 1, null));
        em.flush();
        for (String ruta : List.of("dashboard", "semana")) {
            var respuesta = mockMvc.perform(get("/api/habitos/usuario/" + usuario.getUsuarioId() + "/" + ruta)
                            .header("Authorization", "Bearer " + token))
                    .andReturn().getResponse();
            assertNotEquals(200, respuesta.getStatus(), ruta);
            assertFalse(respuesta.getContentAsString().contains("Leer"), ruta);
        }
    }

    // ── /hoy mirando una semana futura ───────────────────────────

    @Test
    void semanaFutura_seJuzgaConElObjetivoDeSuLunes() {
        ZoneId madrid = ZoneId.of("Europe/Madrid");
        LocalDate lunesQueViene = LocalDate.now(madrid).with(DayOfWeek.MONDAY).plusWeeks(1);
        Habito leer = habito(new Objetivo(Frecuencia.DIARIO, 1, null));
        em.persist(new HabitoObjetivo(em.find(Habito.class, leer.getHabitoId()), lunesQueViene,
                new Objetivo(Frecuencia.SEMANAL, 2, null)));
        em.flush();
        em.clear();

        HoyDTO hoy = hoyService.obtenerHoy(
                em.find(Usuario.class, usuario.getUsuarioId()), lunesQueViene);
        assertEquals(1, hoy.deLaSemana().habitos().size(), "la semana que viene ya es semanal flexible");
        assertEquals(2, hoy.deLaSemana().habitos().get(0).meta());
        assertTrue(hoy.delDia().habitos().isEmpty());
    }
}
