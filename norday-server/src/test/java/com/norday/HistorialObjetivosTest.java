package com.norday;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * El historial de objetivos con la aplicación ensamblada: cada día se juzga
 * con el objetivo vigente en esa fecha, y cambiar la meta o la frecuencia
 * nunca reinterpreta un periodo que ya tiene registros.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe, que es por
 * donde leen el reloj HabitoService.actualizar y RegistroService. La semana
 * en curso empieza el lunes 5; la siguiente, el lunes 12.
 *
 * Las versiones se leen con SQL nativo sobre habito_objetivo, sin pasar por
 * la entidad.
 */
@SpringBootTest
@Transactional
class HistorialObjetivosTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 10);
    private static final LocalDate LUNES_SIGUIENTE = LocalDate.of(2026, 10, 12);

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private RegistroService registroService;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private int usuarioId;
    private int habitoId;

    private void unHabito(Frecuencia frecuencia, int meta, String diasSemana) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Objetivo");
        usuario.setUsername("test-objetivo-" + System.nanoTime());
        usuario.setEmail("test-objetivo-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Leer", "Descripción", frecuencia, meta, usuario, null, INICIO);
        habito.setDiasSemana(diasSemana);
        em.persist(habito);
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        usuarioId = usuario.getUsuarioId();
        habitoId = habito.getHabitoId();

        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    private void registros(LocalDate... fechas) {
        Habito habito = em.find(Habito.class, habitoId);
        for (LocalDate fecha : fechas) {
            em.persist(new Registro(habito, true, null, fecha));
        }
        em.flush();
        em.clear();
    }

    /** El hábito tal como lo arma el controlador a partir del cuerpo del PUT. */
    private Habito editado(Frecuencia frecuencia, int meta, String diasSemana) {
        return editado("Leer", frecuencia, meta, diasSemana);
    }

    private Habito editado(String nombre, Frecuencia frecuencia, int meta, String diasSemana) {
        Habito habito = new Habito();
        habito.setHabitoId(habitoId);
        habito.setNombre(nombre);
        habito.setDescripcion("Descripción");
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setPropietario(em.find(Usuario.class, usuarioId));
        return habito;
    }

    private Habito habito() {
        em.flush();
        em.clear();
        return em.find(Habito.class, habitoId);
    }

    /** Las versiones del hábito, de la más antigua a la más reciente: "desde frecuencia meta días". */
    @SuppressWarnings("unchecked")
    private List<String> versiones() {
        em.flush();
        em.clear();
        List<Object[]> filas = em.createNativeQuery(
                        "SELECT desde, frecuencia, meta, dias_semana FROM habito_objetivo "
                                + "WHERE habito_ref = :habito ORDER BY desde")
                .setParameter("habito", habitoId)
                .getResultList();
        return filas.stream().map(f -> f[0] + " " + f[1] + " " + f[2] + " " + f[3]).toList();
    }

    // ── a. Subir la meta no reinterpreta los días ya hechos ──────────

    @Test
    void subirLaMeta_losDiasYaHechosSiguenContando() {
        // Cinco días hechos con meta 1. Al subir la meta a 2, hoy bajaban a
        // cero y los logros de constancia no veían ninguno.
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(LUNES.minusDays(7), LUNES.minusDays(6), LUNES.minusDays(5),
                LUNES.minusDays(4), LUNES.minusDays(3));

        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);

        // Primer paso de dos de hoy: el día no está hecho, pero los cinco
        // anteriores sí, así que ya llega al logro de 3 días.
        Map<String, Object> primero = registroService.completarHabito(habito(), "", VIERNES);
        @SuppressWarnings("unchecked")
        List<String> logros = (List<String>) primero.get("logros");
        assertTrue(logros.contains("RACHA_3"), "logros: " + logros);
    }

    @Test
    void subirLaMeta_yCompletarElDia_cruzaElHitoContandoLosDiasAnteriores() {
        // Seis días hechos con meta 1; hoy, con meta 2, el séptimo.
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(LUNES.minusDays(7), LUNES.minusDays(6), LUNES.minusDays(5),
                LUNES.minusDays(4), LUNES.minusDays(3), LUNES.minusDays(2));

        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);

        Map<String, Object> primero = registroService.completarHabito(habito(), "", VIERNES);
        Map<String, Object> segundo = registroService.completarHabito(habito(), "", VIERNES);

        assertEquals(0, primero.get("puntosHito"));
        assertEquals(100, segundo.get("puntosHito"));
    }

    // ── b. La regla de cambios ───────────────────────────────────────

    @Test
    void diario_cambioDeMeta_conRegistrosHoy_seAplicaManana() {
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(VIERNES);

        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);

        assertEquals(1, habito().getMeta());
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-10 DIARIO 2 null"), versiones());
    }

    @Test
    void diario_cambioDeMeta_sinRegistrosHoy_seAplicaHoy_aunqueHayaOtrosEstaSemana() {
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(LUNES, LUNES.plusDays(1));

        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);

        assertEquals(2, habito().getMeta());
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-09 DIARIO 2 null"), versiones());
    }

    @Test
    void semanal_cambioDeDiasPorSemana_conRegistrosEstaSemana_seAplicaElLunesSiguiente() {
        unHabito(Frecuencia.SEMANAL, 3, null);
        registros(LUNES.plusDays(1));

        habitoService.actualizar(editado(Frecuencia.SEMANAL, 4, null), null);

        assertEquals(3, habito().getMeta());
        assertEquals(List.of("2026-01-01 SEMANAL 3 null", "2026-10-12 SEMANAL 4 null"), versiones());
    }

    @Test
    void semanal_cambioDeDiasPorSemana_sinRegistrosEstaSemana_seAplicaHoy() {
        unHabito(Frecuencia.SEMANAL, 3, null);
        registros(LUNES.minusDays(6), LUNES.minusDays(4));

        habitoService.actualizar(editado(Frecuencia.SEMANAL, 4, null), null);

        assertEquals(4, habito().getMeta());
        assertEquals(List.of("2026-01-01 SEMANAL 3 null", "2026-10-09 SEMANAL 4 null"), versiones());
    }

    @Test
    void semanal_cambioDeDiasFijos_conRegistrosEstaSemana_seAplicaElLunesSiguiente() {
        // Los mismos tres días por semana, en otros días: la meta no cambia,
        // pero los días fijos sí, y su periodo es la semana.
        unHabito(Frecuencia.SEMANAL, 3, "1,3,5");
        registros(LUNES);

        habitoService.actualizar(editado(Frecuencia.SEMANAL, 3, "2,4,6"), null);

        assertEquals("1,3,5", habito().getDiasSemana());
        assertEquals(List.of("2026-01-01 SEMANAL 3 1,3,5", "2026-10-12 SEMANAL 3 2,4,6"), versiones());
    }

    @Test
    void cambioDeFrecuencia_conRegistrosEstaSemana_seAplicaElLunesSiguiente_noManana() {
        // Diario a semanal con un registro el lunes y ninguno hoy: el
        // periodo del cambio es la semana, no el día.
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(LUNES);

        habitoService.actualizar(editado(Frecuencia.SEMANAL, 3, null), null);

        assertEquals(Frecuencia.DIARIO, habito().getFrecuencia());
        assertEquals(1, habito().getMeta());
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-12 SEMANAL 3 null"), versiones());
    }

    @Test
    void cambioDeFrecuencia_sinRegistrosEstaSemana_seAplicaHoy() {
        unHabito(Frecuencia.SEMANAL, 3, null);
        registros(LUNES.minusDays(3));

        habitoService.actualizar(editado(Frecuencia.DIARIO, 1, null), null);

        assertEquals(Frecuencia.DIARIO, habito().getFrecuencia());
        assertEquals(List.of("2026-01-01 SEMANAL 3 null", "2026-10-09 DIARIO 1 null"), versiones());
    }

    // ── c. Un cambio programado se reemplaza, no se apila ────────────

    @Test
    void unCambioProgramado_seReemplazaSiLlegaOtroAntesDeSuFecha() {
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(VIERNES);

        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);
        habitoService.actualizar(editado(Frecuencia.DIARIO, 3, null), null);
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-10 DIARIO 3 null"), versiones());

        // Otro cambio con otra fecha de efecto: sigue habiendo una sola
        // versión futura, la nueva.
        habitoService.actualizar(editado(Frecuencia.SEMANAL, 4, null), null);
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-12 SEMANAL 4 null"), versiones());
        assertEquals(1, habito().getMeta());
        assertEquals(Frecuencia.DIARIO, habito().getFrecuencia());
    }

    @Test
    void editarSoloElNombre_noCancelaElCambioProgramado() {
        // La app manda el formulario entero, con el objetivo de hoy. Lo que
        // el usuario no ha tocado no cambia: el cambio programado sigue.
        unHabito(Frecuencia.DIARIO, 1, null);
        registros(VIERNES);
        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);

        habitoService.actualizar(editado("Leer cada noche", Frecuencia.DIARIO, 1, null), null);

        assertEquals("Leer cada noche", habito().getNombre());
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-10 DIARIO 2 null"), versiones());
    }

    // ── d. Un hábito sin registros aplica cualquier cambio al momento ─

    @Test
    void sinNingunRegistro_cualquierCambioSeAplicaAlMomento() {
        unHabito(Frecuencia.DIARIO, 1, null);

        habitoService.actualizar(editado(Frecuencia.DIARIO, 2, null), null);
        assertEquals(2, habito().getMeta());
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-09 DIARIO 2 null"), versiones());

        // Y un segundo cambio el mismo día reemplaza la versión de hoy.
        habitoService.actualizar(editado(Frecuencia.SEMANAL, 3, "1,3,5"), null);
        Habito ahora = habito();
        assertEquals(Frecuencia.SEMANAL, ahora.getFrecuencia());
        assertEquals("1,3,5", ahora.getDiasSemana());
        assertEquals(List.of("2026-01-01 DIARIO 1 null", "2026-10-09 SEMANAL 3 1,3,5"), versiones());
    }
}
