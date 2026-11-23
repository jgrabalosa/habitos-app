package com.norday;

import com.norday.core.model.Usuario;
import com.norday.core.security.JwtUtil;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.dto.SemanaPasadaDTO;
import com.norday.habitos.service.HoyService;
import com.norday.habitos.service.SemanaPasadaService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * «Así fue tu semana» contra la base de datos real. La semana pasada de los
 * casos es del lunes 16 al domingo 22 de noviembre de 2026; "hoy" es el
 * miércoles 25 salvo donde se dice otra cosa.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class SemanaPasadaTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 11, 16);
    private static final LocalDate HOY = LocalDate.of(2026, 11, 25);
    private static final LocalDate CREADO = LocalDate.of(2026, 10, 1);

    @Autowired
    private SemanaPasadaService semanaPasadaService;

    @Autowired
    private HoyService hoyService;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtUtil jwtUtil;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario(String zona) {
        String sufijo = String.valueOf(System.nanoTime());
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Semana");
        usuario.setUsername("test-semana-" + sufijo);
        usuario.setEmail("test-semana-" + sufijo + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria(zona);
        em.persist(usuario);
        return usuario;
    }

    /** Un hábito creado el 1 de octubre, con el ancla de su ritmo el lunes 16. */
    private Habito habito(Usuario usuario, String nombre, Frecuencia frecuencia, int meta) {
        Habito habito = new Habito(nombre, "", frecuencia, meta, usuario, null, CREADO);
        em.persist(habito);
        em.persist(new Ritmo(habito, LUNES));
        return habito;
    }

    private void hecho(Habito habito, LocalDate... dias) {
        for (LocalDate dia : dias) {
            em.persist(new Registro(habito, true, "", dia));
        }
    }

    private LocalDate dia(int delMes) {
        return LocalDate.of(2026, 11, delMes);
    }

    private void aLaBase() {
        em.flush();
        em.clear();
    }

    @Test
    void unaSemanaConDiasCompletosYUnSemanalCompleto() {
        Usuario usuario = usuario("Europe/Madrid");
        Habito leer = habito(usuario, "Leer", Frecuencia.DIARIO, 1);
        Habito agua = habito(usuario, "Agua", Frecuencia.DIARIO, 2);
        Habito gimnasio = habito(usuario, "Gimnasio", Frecuencia.SEMANAL, 3);
        gimnasio.setIcono("pesas");
        Habito yoga = habito(usuario, "Yoga", Frecuencia.SEMANAL, 2);
        // Leer, de lunes a viernes; agua (2 al día), lunes a miércoles.
        hecho(leer, dia(16), dia(17), dia(18), dia(19), dia(20));
        hecho(agua, dia(16), dia(16), dia(17), dia(17), dia(18), dia(18));
        // Gimnasio llega a sus 3; yoga se queda en 1 de 2.
        hecho(gimnasio, dia(16), dia(18), dia(20));
        hecho(yoga, dia(21));
        aLaBase();

        SemanaPasadaDTO semana = semanaPasadaService.obtener(em.find(Usuario.class, usuario.getUsuarioId()), HOY);

        assertEquals("2026-11-16", semana.desde());
        assertEquals("2026-11-22", semana.hasta());
        assertTrue(semana.hayActividad());
        // Dos diarios los siete días = 14 que tocaban; lunes a miércoles los
        // dos, jueves y viernes solo leer = 8 hechos. Los semanales no tocan
        // en la sección del día.
        assertEquals(14, semana.total());
        assertEquals(8, semana.hechos());
        assertEquals(3, semana.diasCompletos());

        assertEquals(2, semana.semanales().size());
        var gim = semana.semanales().stream().filter(s -> s.nombre().equals("Gimnasio")).findFirst().orElseThrow();
        assertEquals(gimnasio.getHabitoId(), gim.habitoId());
        assertEquals("pesas", gim.icono());
        assertEquals(3, gim.hechos());
        assertEquals(3, gim.meta());
        assertTrue(gim.completo());
        var yog = semana.semanales().stream().filter(s -> s.nombre().equals("Yoga")).findFirst().orElseThrow();
        assertEquals(1, yog.hechos());
        assertEquals(2, yog.meta());
        assertFalse(yog.completo());

        assertNotNull(semana.ritmoNivel());
        assertNotNull(semana.ritmoTendencia());
        // El semanal completo pasa de 0,5 a 0,75 (nivel 2 a 3), más que
        // leer (0,5 a 0,65, también de 2 a 3); agua y yoga no cambian de nivel.
        assertNotNull(semana.masSube());
        assertEquals(gimnasio.getHabitoId(), semana.masSube().habitoId());
        assertEquals("Gimnasio", semana.masSube().nombre());
        assertEquals(2, semana.masSube().nivelAntes());
        assertEquals(3, semana.masSube().nivelAhora());
    }

    @Test
    void unaSemanaSinActividad() {
        Usuario usuario = usuario("Europe/Madrid");
        habito(usuario, "Leer", Frecuencia.DIARIO, 1);
        habito(usuario, "Gimnasio", Frecuencia.SEMANAL, 3);
        aLaBase();

        SemanaPasadaDTO semana = semanaPasadaService.obtener(em.find(Usuario.class, usuario.getUsuarioId()), HOY);

        assertFalse(semana.hayActividad());
        assertEquals(7, semana.total());
        assertEquals(0, semana.hechos());
        assertEquals(0, semana.diasCompletos());
        assertEquals(1, semana.semanales().size());
        assertFalse(semana.semanales().get(0).completo());
        assertNull(semana.masSube(), "sin hacer nada ningún ritmo sube");
    }

    @Test
    void laSemanaPasadaEsLaDeLaZonaDelUsuario() {
        // Domingo 22-nov 20:00 UTC: en Madrid sigue siendo domingo (la
        // semana pasada es la del 9 al 15); en Kiritimati (UTC+14) ya es el
        // lunes 23, y la semana pasada es la del 16 al 22.
        Instant ahora = Instant.parse("2026-11-22T20:00:00Z");
        Usuario madrid = usuario("Europe/Madrid");
        Usuario kiritimati = usuario("Pacific/Kiritimati");
        for (Usuario quien : new Usuario[] {madrid, kiritimati}) {
            Habito leer = habito(quien, "Leer", Frecuencia.DIARIO, 1);
            hecho(leer, dia(16));
        }
        aLaBase();

        SemanaPasadaDTO enMadrid = semanaPasadaService.obtener(
                em.find(Usuario.class, madrid.getUsuarioId()), ahora);
        SemanaPasadaDTO enKiritimati = semanaPasadaService.obtener(
                em.find(Usuario.class, kiritimati.getUsuarioId()), ahora);

        assertEquals("2026-11-09", enMadrid.desde());
        assertEquals("2026-11-15", enMadrid.hasta());
        assertFalse(enMadrid.hayActividad());
        assertEquals("2026-11-16", enKiritimati.desde());
        assertEquals("2026-11-22", enKiritimati.hasta());
        assertTrue(enKiritimati.hayActividad());
        assertEquals(1, enKiritimati.hechos());
    }

    @Test
    void unHabitoEnPausaNoCuentaComoEnHoy() {
        Usuario usuario = usuario("Europe/Madrid");
        Habito leer = habito(usuario, "Leer", Frecuencia.DIARIO, 1);
        Habito pausado = habito(usuario, "Pausado", Frecuencia.DIARIO, 1);
        Habito pausadoSemanal = habito(usuario, "Pausado semanal", Frecuencia.SEMANAL, 1);
        hecho(leer, dia(16));
        hecho(pausado, dia(16), dia(17));
        hecho(pausadoSemanal, dia(16));
        pausado.setActivo(false);
        pausadoSemanal.setActivo(false);
        aLaBase();
        Usuario recargado = em.find(Usuario.class, usuario.getUsuarioId());

        SemanaPasadaDTO semana = semanaPasadaService.obtener(recargado, HOY);

        // Solo cuenta leer: 7 días que tocaban, 1 hecho. Y ningún semanal.
        assertEquals(7, semana.total());
        assertEquals(1, semana.hechos());
        assertTrue(semana.semanales().isEmpty());
        // Hoy lo trata igual: solo el diario activo en la sección del día.
        var hoy = hoyService.obtenerHoy(recargado, HOY, HOY);
        assertEquals(1, hoy.delDia().total());
        assertEquals(0, hoy.deLaSemana().total());
    }

    @Test
    void noEscribeNada_niElAncla_niLaQueFalta() {
        Usuario usuario = usuario("Europe/Madrid");
        // Un ancla antigua que Hoy movería al lunes, y un hábito sin ancla.
        Habito conAncla = new Habito("Leer", "", Frecuencia.DIARIO, 1, usuario, null, CREADO);
        em.persist(conAncla);
        em.persist(new Ritmo(conAncla, CREADO));
        Habito sinAncla = new Habito("Correr", "", Frecuencia.DIARIO, 1, usuario, null, CREADO);
        em.persist(sinAncla);
        hecho(conAncla, dia(16));
        aLaBase();

        semanaPasadaService.obtener(em.find(Usuario.class, usuario.getUsuarioId()), HOY);
        aLaBase();

        Ritmo ancla = em.createQuery("SELECT r FROM Ritmo r WHERE r.habito.habitoId = :id", Ritmo.class)
                .setParameter("id", conAncla.getHabitoId()).getSingleResult();
        assertEquals(CREADO, ancla.getDesde());
        assertEquals(0.5, ancla.getValor(), 0.0000001);
        assertNull(ancla.getDesdePrevio());
        long sin = em.createQuery("SELECT COUNT(r) FROM Ritmo r WHERE r.habito.habitoId = :id", Long.class)
                .setParameter("id", sinAncla.getHabitoId()).getSingleResult();
        assertEquals(0, sin, "leer no crea el ancla que falta");
    }

    @Test
    void elEndpointDevuelveElContrato() throws Exception {
        Usuario usuario = usuario("Europe/Madrid");
        // Con el reloj real: el ancla, en hoy, para que el ritmo sea el de partida.
        Habito leer = new Habito("Leer", "", Frecuencia.DIARIO, 1, usuario, null,
                LocalDate.now(ZoneId.of("Europe/Madrid")).minusDays(60));
        em.persist(leer);
        em.persist(new Ritmo(leer, LocalDate.now(ZoneId.of("Europe/Madrid"))));
        aLaBase();
        String token = jwtUtil.generateToken(usuario.getUsuarioId(), usuario.getEmail());

        mockMvc.perform(get("/api/habitos/usuario/" + usuario.getUsuarioId() + "/semana-pasada")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.desde").isString())
                .andExpect(jsonPath("$.hasta").isString())
                .andExpect(jsonPath("$.hayActividad").value(false))
                .andExpect(jsonPath("$.hechos").value(0))
                .andExpect(jsonPath("$.total").value(7))
                .andExpect(jsonPath("$.diasCompletos").value(0))
                .andExpect(jsonPath("$.semanales").isArray())
                .andExpect(jsonPath("$.ritmoNivel").value(2))
                .andExpect(jsonPath("$.ritmoTendencia").value("IGUAL"))
                .andExpect(jsonPath("$.masSube").value((Object) null));
    }
}
