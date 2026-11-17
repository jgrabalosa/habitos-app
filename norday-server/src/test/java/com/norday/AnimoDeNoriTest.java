package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.core.service.UsuarioService;
import com.norday.enfoque.service.CumplimientoDiarioEnfoque;
import com.norday.enfoque.service.RelojEnfoque;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.gamificacion.service.MascotaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.CumplimientoDiarioHabitos;
import com.norday.habitos.service.HabitoService;
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

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.doReturn;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El ánimo de Nori con Norday Habits y Norday Focus a la vez (16-nov-2026),
 * de punta a punta: GET /api/mascota con las dos apps de verdad y Postgres.
 *
 * Norday Habits mira su «hoy» con el reloj del sistema, así que aquí «hoy» es
 * el día de verdad en Madrid. El reloj de Focus se fija a las 12:00 de ese
 * día, y las sesiones empiezan a las 9:00, para que la prueba no dependa de
 * la hora a la que corre.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AnimoDeNoriTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtUtil jwtUtil;
    @Autowired private UsuarioService usuarioService;
    @Autowired private RegistroService registroService;
    @Autowired private HabitoService habitoService;
    @Autowired private MascotaService mascotaService;
    @Autowired private CumplimientoDiarioHabitos cumplimientoHabitos;
    @Autowired private CumplimientoDiarioEnfoque cumplimientoEnfoque;
    @Autowired private TransactionTemplate tx;

    @MockitoSpyBean
    private RelojEnfoque reloj;

    @PersistenceContext
    private EntityManager em;

    private final List<Integer> usuarios = new ArrayList<>();
    private LocalDate hoy;

    @AfterEach
    void borrar() {
        for (Integer id : usuarios) {
            usuarioService.eliminarCuenta(id);
        }
    }

    // ── Datos ────────────────────────────────────────────────────

    /** Un usuario dado de alta hace {@code diasDeAlta} días. */
    private int usuario(int diasDeAlta) {
        hoy = LocalDate.now(MADRID);
        doReturn(hoy.atTime(12, 0).atZone(MADRID).toInstant()).when(reloj).ahora();
        int id = tx.execute(estado -> {
            Usuario u = new Usuario();
            u.setNombre("Test Animo");
            u.setUsername("test-animo-" + System.nanoTime());
            u.setEmail("test-animo-" + System.nanoTime() + "@example.com");
            u.setContrasena("contrasenaDePrueba");
            u.setProveedorAuth("LOCAL");
            u.setFechaRegistro(LocalDateTime.now().minusDays(diasDeAlta));
            u.setIdioma("es");
            u.setZonaHoraria("Europe/Madrid");
            em.persist(u);
            return u.getUsuarioId();
        });
        usuarios.add(id);
        mascotaService.obtenerOCrear(id);
        return id;
    }

    private int habitoDeVecesDiario(int usuarioId) {
        LocalDate inicio = hoy.minusDays(60);
        Objetivo objetivo = new Objetivo(Unidad.VECES, Frecuencia.DIARIO, 1, null, null);
        return tx.execute(estado -> {
            Usuario u = em.find(Usuario.class, usuarioId);
            Habito h = new Habito("Beber agua", null, Frecuencia.DIARIO, 1, u, null, inicio);
            h.setUnidad(Unidad.VECES);
            em.persist(h);
            em.persist(new HabitoObjetivo(h, inicio, objetivo));
            em.persist(new Racha(h, inicio));
            em.persist(new Ritmo(h, inicio));
            return h.getHabitoId();
        });
    }

    private void completarHoy(int habitoId) {
        tx.execute(estado -> registroService.completar(habitoService.buscarPorId(habitoId),
                new Solicitud(null, hoy, null, UUID.randomUUID(), OrigenRegistro.MANUAL)));
    }

    /** Una sesión completada sin destino que empieza a esa hora de Madrid de hoy. */
    private void sesionHoy(int usuarioId, int hora, int minutos) throws Exception {
        Instant inicio = hoy.atTime(hora, 0).atZone(MADRID).toInstant();
        String cuerpo = "{\"clave\":\"" + UUID.randomUUID() + "\",\"inicioUtc\":\"" + inicio + "\",\"duracionSeg\":"
                + minutos * 60 + ",\"duracionObjetivoMin\":" + minutos + ",\"estado\":\"COMPLETADA\",\"destino\":null}";
        mockMvc.perform(post("/api/enfoque/sesiones")
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(usuarioId, "animo@example.com"))
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isCreated());
    }

    private String animo(int usuarioId) throws Exception {
        String json = mockMvc.perform(get("/api/mascota/" + usuarioId)
                        .header("Authorization", "Bearer " + jwtUtil.generateToken(usuarioId, "animo@example.com")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int desde = json.indexOf("\"animo\":\"") + 9;
        return json.substring(desde, json.indexOf('"', desde));
    }

    // ── Solo Norday Habits: igual que antes ──────────────────────

    @Test
    void soloHabits_elAnimoEsElDeHabits_sinCambios() throws Exception {
        int a = usuario(60);
        int agua = habitoDeVecesDiario(a);
        assertTrue(cumplimientoHabitos.enUso(a));
        assertFalse(cumplimientoEnfoque.enUso(a));

        assertEquals(AvanceDeHoy.AUSENTE, cumplimientoHabitos.comoVaHoy(a));
        assertEquals("triste", animo(a), "lleva tiempo sin hacer nada");

        completarHoy(agua);
        assertEquals(AvanceDeHoy.COMPLETO, cumplimientoHabitos.comoVaHoy(a));
        assertEquals("exultante", animo(a));
    }

    @Test
    void sinNingunaApp_elRecienLlegado_laVeDormida_yElAntiguo_triste() throws Exception {
        assertEquals("dormida", animo(usuario(0)));
        assertEquals("triste", animo(usuario(60)));
    }

    // ── Solo Norday Focus ────────────────────────────────────────

    @Test
    void soloFocus_feliz_conUnaSesion_yExultante_conElDiaDeEnfoque() throws Exception {
        int a = usuario(60);
        assertEquals("triste", animo(a), "antes de nada, como hoy");

        sesionHoy(a, 9, 25);
        assertTrue(cumplimientoEnfoque.enUso(a));
        assertFalse(cumplimientoHabitos.enUso(a), "sin hábitos, Habits no cuenta");
        assertEquals("feliz", animo(a), "antes, triste para siempre: Habits no sabía de Focus");

        sesionHoy(a, 10, 25);
        assertEquals("exultante", animo(a), "50 minutos: día de enfoque");
    }

    // ── Las dos ──────────────────────────────────────────────────

    @Test
    void lasDos_exultante_soloConLasDosCumplidas() throws Exception {
        int a = usuario(60);
        int agua = habitoDeVecesDiario(a);

        sesionHoy(a, 9, 25);
        assertEquals("feliz", animo(a), "algo hecho en Focus, nada en Habits");

        completarHoy(agua);
        assertEquals("feliz", animo(a), "Habits cumplido, Focus a medias: aún no es todo");

        sesionHoy(a, 10, 25);
        assertEquals("exultante", animo(a));
    }
}
