package com.norday;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
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
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
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

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El completado de minutos de punta a punta, con la aplicación ensamblada y
 * Postgres de verdad: los campos nuevos, los códigos de estado, la clave del
 * cliente, el premio, deshacer y las reglas que solo valen para VECES.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe. Las cifras
 * del premio salen del diseño (sección 2), no de CalculoPremio.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CompletarMinutosTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);
    private static final LocalDate JUEVES = LUNES.plusDays(3);
    private static final LocalDate VIERNES = LUNES.plusDays(4);

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private RegistroService registroService;
    @Autowired private UsuarioMonedaService usuarioMonedaService;
    @Autowired private MascotaService mascotaService;
    @Autowired private IRegistroDAO registroDAO;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;
    private String token;

    private Usuario usuario() {
        String email = "test-cmin-" + System.nanoTime() + "@example.com";
        Usuario u = new Usuario();
        u.setNombre("Test Completar Minutos");
        u.setUsername("test-cmin-" + System.nanoTime());
        u.setEmail(email);
        u.setContrasena("contrasenaDePrueba");
        u.setProveedorAuth("LOCAL");
        u.setFechaRegistro(LocalDateTime.now());
        u.setIdioma("es");
        u.setZonaHoraria("Europe/Madrid");
        em.persist(u);
        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
        return u;
    }

    private Habito habito(Objetivo objetivo) {
        if (usuario == null) {
            usuario = usuario();
            token = jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());
        }
        Habito habito = new Habito("Hábito", null, objetivo.frecuencia(), objetivo.meta(), usuario, null, INICIO);
        habito.setUnidad(objetivo.unidad());
        habito.setMinutosObjetivo(objetivo.minutosObjetivo());
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, objetivo));
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        return habito;
    }

    private static Objetivo minutosDiario(int minutos) {
        return new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, minutos, null);
    }

    private Habito leido(Habito habito) {
        em.flush();
        em.clear();
        return em.find(Habito.class, habito.getHabitoId());
    }

    private void registro(Habito habito, LocalDate fecha, int minutos) {
        Registro r = new Registro(em.find(Habito.class, habito.getHabitoId()), true, null, fecha);
        r.setCantidad(minutos);
        em.persist(r);
        em.flush();
    }

    private int saldo() {
        em.flush();
        return usuarioMonedaService.consultarSaldo(usuario.getUsuarioId());
    }

    private int xp() {
        em.flush();
        em.clear();
        return mascotaService.obtenerOCrear(usuario.getUsuarioId()).getExperiencia();
    }

    private ResultActions completar(Habito habito, String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/registros/completar/" + habito.getHabitoId())
                .header("Authorization", "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(cuerpo));
    }

    private Map<String, Object> paso(Habito habito, LocalDate fecha, String minutos) {
        return registroService.completar(leido(habito),
                new Solicitud(null, fecha, minutos, null, OrigenRegistro.MANUAL));
    }

    // ── Endpoint ─────────────────────────────────────────────────

    @Test
    void endpoint_minutosPideLosMinutos_yValidaLoQueLlega() throws Exception {
        Habito leer = habito(minutosDiario(30));
        completar(leer, "{}").andExpect(status().isConflict())
                .andExpect(jsonPath("$.mensaje").value(containsString("Actualiza Norday Habits")));
        for (String malos : List.of("\"0\"", "\"601\"", "\"abc\"")) {
            completar(leer, "{\"minutos\":" + malos + "}").andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje").value(containsString("entre 1 y 600")));
        }
        completar(leer, "{\"minutos\":\"10\",\"clave\":\"no-es-una-clave\"}").andExpect(status().isBadRequest());
        completar(leer, "{\"minutos\":\"10\",\"origen\":\"OTRO\"}").andExpect(status().isBadRequest());
        assertEquals(0, registroDAO.findByHabito(leido(leer)).size(), "nada de eso crea registros");
    }

    @Test
    void endpoint_pasosDeMinutos_conClave_ySinRepetir() throws Exception {
        Habito leer = habito(minutosDiario(30));
        String clave = UUID.randomUUID().toString();

        completar(leer, "{\"minutos\":\"15\",\"clave\":\"" + clave + "\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.puntosPaso").value(12))
                .andExpect(jsonPath("$.xpGanada").value(2))
                .andExpect(jsonPath("$.minutosDia").value(15))
                .andExpect(jsonPath("$.minutosObjetivo").value(30))
                .andExpect(jsonPath("$.diaHecho").value(false))
                .andExpect(jsonPath("$.mostrarValoracion").value(false));
        int registroId = registroDAO.findByHabito(leido(leer)).get(0).getRegistroId();
        int saldoTrasElPrimero = saldo();

        // El reintento con la misma clave: 200, el mismo registro, nada pagado.
        completar(leer, "{\"minutos\":\"15\",\"clave\":\"" + clave + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Hábito completado correctamente"))
                .andExpect(jsonPath("$.registroId").value(registroId))
                .andExpect(jsonPath("$.puntosGanados").value(0))
                .andExpect(jsonPath("$.puntosPaso").value(0))
                .andExpect(jsonPath("$.xpGanada").value(0))
                .andExpect(jsonPath("$.logrosOtorgados").isEmpty())
                .andExpect(jsonPath("$.minutosDia").value(15))
                .andExpect(jsonPath("$.diaHecho").value(false));
        assertEquals(1, registroDAO.findByHabito(leido(leer)).size());
        assertEquals(saldoTrasElPrimero, saldo());

        // El segundo paso completa el día; sale la valoración.
        completar(leer, "{\"minutos\":\"15\",\"clave\":\"" + UUID.randomUUID() + "\",\"origen\":\"ENFOQUE\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.puntosPaso").value(13))
                .andExpect(jsonPath("$.xpGanada").value(3))
                .andExpect(jsonPath("$.minutosDia").value(30))
                .andExpect(jsonPath("$.diaHecho").value(true))
                .andExpect(jsonPath("$.mostrarValoracion").value(true));

        // Los minutos de más se guardan, pero no dan nada ni vuelven a sacar la valoración.
        completar(leer, "{\"minutos\":\"10\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.puntosPaso").value(0))
                .andExpect(jsonPath("$.xpGanada").value(0))
                .andExpect(jsonPath("$.minutosDia").value(40))
                .andExpect(jsonPath("$.diaHecho").value(true))
                .andExpect(jsonPath("$.mostrarValoracion").value(false));

        mockMvc.perform(get("/api/registros/habito/" + leer.getHabitoId())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].cantidad").exists())
                .andExpect(jsonPath("$[0].origen").exists())
                .andExpect(jsonPath("$[0]", not(hasKey("claveCliente"))));
        List<Registro> registros = registroDAO.findByHabito(leido(leer));
        assertEquals(40, registros.stream().mapToInt(Registro::getCantidad).sum());
        assertEquals(1, registros.stream().filter(r -> r.getOrigen() == OrigenRegistro.ENFOQUE).count());

        mockMvc.perform(get("/api/registros/habito/" + leer.getHabitoId() + "/hoy")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.completadoHoy").value(true))
                .andExpect(jsonPath("$.completadosPeriodo").value(1))
                .andExpect(jsonPath("$.meta").value(1))
                .andExpect(jsonPath("$.minutosHoy").value(40))
                .andExpect(jsonPath("$.minutosObjetivo").value(30));
    }

    @Test
    void endpoint_unaClaveDeOtroHabito_esUnConflicto() throws Exception {
        Habito leer = habito(minutosDiario(30));
        Habito correr = habito(minutosDiario(20));
        String clave = UUID.randomUUID().toString();
        completar(leer, "{\"minutos\":\"10\",\"clave\":\"" + clave + "\"}").andExpect(status().isCreated());
        completar(correr, "{\"minutos\":\"10\",\"clave\":\"" + clave + "\"}").andExpect(status().isConflict());
        assertEquals(0, registroDAO.findByHabito(leido(correr)).size());
    }

    @Test
    void endpoint_enVecesLosMinutosSeIgnoran() throws Exception {
        Habito veces = habito(new Objetivo(Frecuencia.DIARIO, 1, null));
        completar(veces, "{\"minutos\":\"abc\"}")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.puntosPaso").value(25))
                .andExpect(jsonPath("$.minutosDia").value(nullValue()))
                .andExpect(jsonPath("$.minutosObjetivo").value(nullValue()))
                .andExpect(jsonPath("$.diaHecho").value(true));
        assertEquals(1, registroDAO.findByHabito(leido(veces)).get(0).getCantidad());
    }

    // ── Reglas y premio ──────────────────────────────────────────

    @Test
    void semanalDeMinutos_variasSesionesElMismoDia_yElTercerDiaHechoTraeElExtra() {
        Habito entrenar = habito(new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, null));
        assertEquals(8, paso(entrenar, LUNES, "20").get("puntosPaso"));
        assertEquals(8, paso(entrenar, LUNES, "20").get("puntosPaso"), "otra sesión el mismo día: permitida");
        Map<String, Object> lunesHecho = paso(entrenar, LUNES, "20");
        assertEquals(9, lunesHecho.get("puntosPaso"));
        assertEquals(true, lunesHecho.get("diaHecho"));
        assertEquals(0, lunesHecho.get("puntosExtraSemana"));
        assertEquals(25, paso(entrenar, MARTES, "60").get("puntosPaso"));
        Map<String, Object> tercero = paso(entrenar, MIERCOLES, "75");
        assertEquals(25, tercero.get("puntosPaso"));
        assertEquals(15, tercero.get("puntosExtraSemana"));
        assertEquals(8, tercero.get("xpGanada"), "5 del día y 3 de extra");
        assertEquals(75, tercero.get("minutosDia"));
        Map<String, Object> cuarto = paso(entrenar, JUEVES, "60");
        assertEquals(0, cuarto.get("puntosGanados"), "la semana ya estaba cumplida");
        assertEquals(false, cuarto.get("mostrarValoracion"), "no es hoy");
    }

    @Test
    void lasReglasDeUnoPorDiaYDeMetaAlcanzada_siguenEnVeces() {
        Habito semanal = habito(new Objetivo(Frecuencia.SEMANAL, 3, null));
        paso(semanal, LUNES, null);
        assertThrows(ConflictoException.class, () -> paso(semanal, LUNES, null));
        Habito diario = habito(new Objetivo(Frecuencia.DIARIO, 1, null));
        paso(diario, LUNES, null);
        assertThrows(ConflictoException.class, () -> paso(diario, LUNES, null));
    }

    @Test
    void unDiaAntesDelPasoAMinutos_seCompletaComoVeces() {
        Habito leer = habito(new Objetivo(Frecuencia.DIARIO, 1, null));
        em.persist(new HabitoObjetivo(em.find(Habito.class, leer.getHabitoId()), JUEVES, minutosDiario(30)));
        em.flush();
        Map<String, Object> martes = paso(leer, MARTES, null);
        assertEquals(25, martes.get("puntosPaso"));
        assertNull(martes.get("minutosDia"));
        assertThrows(ConflictoException.class, () -> paso(leer, VIERNES, null));
        assertEquals(12, paso(leer, VIERNES, "15").get("puntosPaso"));
    }

    @Test
    void laVuelta_llegaConElPasoQueCompletaElDia() {
        Habito leer = habito(minutosDiario(30));
        registro(leer, LUNES.minusWeeks(1), 30);
        @SuppressWarnings("unchecked")
        List<String> primero = (List<String>) paso(leer, VIERNES, "10").get("logros");
        assertFalse(primero.contains("RACHA_RECUPERADA"), "un paso a medias todavía no es volver");
        @SuppressWarnings("unchecked")
        List<String> segundo = (List<String>) paso(leer, VIERNES, "20").get("logros");
        assertTrue(segundo.contains("RACHA_RECUPERADA"));
    }

    // ── Deshacer ─────────────────────────────────────────────────

    @Test
    void deshacerUnPasoDeMinutos_devuelveExactamenteLoQueDio() {
        Habito leer = habito(minutosDiario(30));
        registro(leer, LUNES, 30);
        registro(leer, MARTES, 30);
        int saldoInicial = saldo();
        int xpInicial = xp();

        Map<String, Object> parcial = paso(leer, VIERNES, "10");
        int saldoTrasParcial = saldo();
        int xpTrasParcial = xp();
        assertEquals(8, parcial.get("puntosPaso"));

        // El que completa el tercer día hecho: su parte, el hito de 3 días
        // (50) y el logro de constancia de 3 días.
        Map<String, Object> completa = paso(leer, VIERNES, "20");
        assertEquals(17, completa.get("puntosPaso"));
        assertEquals(50, completa.get("puntosHito"));
        @SuppressWarnings("unchecked")
        List<String> logros = (List<String>) completa.get("logros");
        assertTrue(logros.contains("RACHA_3"));
        assertTrue(saldo() > saldoTrasParcial + 67, "también paga el logro");
        Number hitos = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM hito_pagado WHERE habito_ref = " + leer.getHabitoId()).getSingleResult();
        assertEquals(1, hitos.intValue());

        Map<String, Object> deshecho = registroService.deshacerRegistro((Integer) completa.get("registroId"));
        assertEquals(saldoTrasParcial, saldo());
        assertEquals(xpTrasParcial, xp());
        assertFalse(((List<?>) deshecho.get("logrosRetirados")).isEmpty());
        hitos = (Number) em.createNativeQuery(
                "SELECT COUNT(*) FROM hito_pagado WHERE habito_ref = " + leer.getHabitoId()).getSingleResult();
        assertEquals(0, hitos.intValue());

        registroService.deshacerRegistro((Integer) parcial.get("registroId"));
        assertEquals(saldoInicial, saldo());
        assertEquals(xpInicial, xp());
    }

    // ── La clave choca en la base de datos sin romper la transacción ──

    @Test
    void guardarConUnaClaveQueYaExiste_noEscribeNada_yLaTransaccionSigueViva() {
        Habito leer = habito(minutosDiario(30));
        UUID clave = UUID.randomUUID();
        Registro primero = new Registro(leido(leer), true, null, VIERNES);
        primero.setCantidad(10);
        primero.setClaveCliente(clave);
        Registro guardado = registroDAO.guardarSiClaveLibre(primero);
        assertNotNull(guardado);
        assertEquals(clave, guardado.getClaveCliente());

        Registro segundo = new Registro(leido(leer), true, null, VIERNES);
        segundo.setCantidad(20);
        segundo.setClaveCliente(clave);
        assertNull(registroDAO.guardarSiClaveLibre(segundo));
        // La transacción no ha quedado marcada para deshacer: se puede seguir.
        assertEquals(guardado.getRegistroId(), registroDAO.findByClave(clave).getRegistroId());
        assertEquals(1, registroDAO.findByHabito(leido(leer)).size());
    }
}
