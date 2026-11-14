package com.norday;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.model.dto.DashboardHabitoDTO;
import com.norday.habitos.model.dto.FilaHoyDTO;
import com.norday.habitos.model.dto.HabitoDetalleDTO;
import com.norday.habitos.model.dto.HabitoDiaDTO;
import com.norday.habitos.model.dto.HabitoResumenDTO;
import com.norday.habitos.model.dto.HoyDTO;
import com.norday.habitos.model.dto.RegistroDiaDTO;
import com.norday.habitos.model.dto.SemanaDashboardDTO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.CumplimientoDiarioHabitos;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.HoyService;
import com.norday.habitos.service.LogrosHabitosService;
import com.norday.habitos.service.RitmoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las lecturas con hábitos de minutos, con la aplicación ensamblada y
 * Postgres de verdad: Hoy, el ánimo, el ritmo, el día completo, las
 * pantallas de la app ya instalada (/dashboard, /semana, /resumen), el
 * detalle, los logros de constancia y el recuento de los logros de volumen.
 *
 * Donde el servicio admite el día de hoy, la semana de referencia empieza el
 * lunes 5-oct-2026 y hoy es el viernes 9. Donde lee el reloj, los registros
 * se ponen en el hoy real de Europe/Madrid.
 */
@SpringBootTest
@Transactional
class MinutosLecturasTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);
    private static final LocalDate JUEVES = LUNES.plusDays(3);
    private static final LocalDate VIERNES = LUNES.plusDays(4);

    @Autowired private HoyService hoyService;
    @Autowired private HabitoService habitoService;
    @Autowired private RitmoService ritmoService;
    @Autowired private CumplimientoDiarioHabitos cumplimiento;
    @Autowired private LogrosHabitosService logrosHabitosService;
    @Autowired private IRegistroDAO registroDAO;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Minutos");
        usuario.setUsername("test-minutos-" + System.nanoTime());
        usuario.setEmail("test-minutos-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        return usuario;
    }

    /** Un hábito con una versión desde su inicio; el ritmo anclado en {@code ancla}. */
    private Habito habito(Usuario usuario, Objetivo objetivo, LocalDate ancla) {
        Habito habito = new Habito("Hábito", null, objetivo.frecuencia(), objetivo.meta(), usuario, null, INICIO);
        habito.setUnidad(objetivo.unidad());
        habito.setMinutosObjetivo(objetivo.minutosObjetivo());
        habito.setDiasSemana(objetivo.diasSemana());
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, objetivo));
        em.persist(new Racha(habito, ancla));
        em.persist(new Ritmo(habito, ancla));
        return habito;
    }

    private Registro registro(Habito habito, LocalDate fecha, int minutos) {
        Registro registro = new Registro(habito, true, null, fecha);
        registro.setCantidad(minutos);
        em.persist(registro);
        return registro;
    }

    private void limpiar() {
        em.flush();
        em.clear();
    }

    private static Objetivo minutosDiario(int minutos) {
        return new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, minutos, null);
    }

    private static FilaHoyDTO fila(List<FilaHoyDTO> filas, int habitoId) {
        return filas.stream().filter(f -> f.habito().getHabitoId() == habitoId).findFirst().orElseThrow();
    }

    // ── Hoy ──────────────────────────────────────────────────────

    @Test
    void hoy_diarioDeMinutos_cadaDiaConSusMinutos_yLaFilaCuentaDias() {
        Usuario usuario = usuario();
        Habito diario = habito(usuario, minutosDiario(30), LUNES);
        // Desde el miércoles, 40 minutos.
        em.persist(new HabitoObjetivo(diario, MIERCOLES, minutosDiario(40)));
        registro(diario, LUNES, 30);
        registro(diario, MARTES, 20);
        registro(diario, MIERCOLES, 35);
        registro(diario, VIERNES, 10);
        registro(diario, VIERNES, 10);
        limpiar();

        HoyDTO viernes = hoyService.obtenerHoy(usuario, VIERNES, VIERNES);
        FilaHoyDTO f = fila(viernes.delDia().habitos(), diario.getHabitoId());
        assertEquals(0, f.completados(), "20 de 40: el día no está hecho");
        assertEquals(1, f.meta());
        assertFalse(f.hecho());
        assertFalse(f.completadoEnFecha());
        assertEquals(Unidad.MINUTOS, f.unidad());
        assertEquals(20, f.minutosHechos());
        assertEquals(40, f.minutosObjetivo());
        assertEquals(List.of(LUNES.toString()), f.fechasCompletadas(),
                "el miércoles (35 de 40) no cuenta: se juzga con los minutos de ese día");
        assertEquals(Unidad.MINUTOS, f.habito().getUnidad());
        assertEquals(40, f.habito().getMinutosObjetivo());

        FilaHoyDTO lunes = fila(hoyService.obtenerHoy(usuario, LUNES, VIERNES).delDia().habitos(),
                diario.getHabitoId());
        assertEquals(1, lunes.completados());
        assertTrue(lunes.hecho());
        assertTrue(lunes.completadoEnFecha());
        assertEquals(30, lunes.minutosHechos());
        assertEquals(30, lunes.minutosObjetivo());

        // La tira: el martes 20 de 30, el miércoles 35 de 40.
        assertEquals(20.0 / 30.0, viernes.dias().get(1).avance(), 1e-9);
        assertEquals(35.0 / 40.0, viernes.dias().get(2).avance(), 1e-9);
        assertEquals(0.5, viernes.avance(), 1e-9);
    }

    @Test
    void hoy_semanalDeMinutos_cuentaDiasHechos() {
        Usuario usuario = usuario();
        Habito semanal = habito(usuario, new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, null), LUNES);
        registro(semanal, LUNES, 60);
        registro(semanal, MARTES, 59);
        registro(semanal, MIERCOLES, 30);
        registro(semanal, MIERCOLES, 30);
        limpiar();

        FilaHoyDTO f = fila(hoyService.obtenerHoy(usuario, VIERNES, VIERNES).deLaSemana().habitos(),
                semanal.getHabitoId());
        assertEquals(2, f.completados(), "lunes y miércoles; el martes se queda en 59");
        assertEquals(3, f.meta());
        assertFalse(f.hecho());
        assertEquals(0, f.minutosHechos());
        assertEquals(60, f.minutosObjetivo());
        assertEquals(List.of(LUNES.toString(), MIERCOLES.toString()), f.fechasCompletadas());
    }

    @Test
    void hoy_vecesSinCambios() {
        Usuario usuario = usuario();
        Habito veces = habito(usuario, new Objetivo(Frecuencia.DIARIO, 2, null), LUNES);
        registro(veces, VIERNES, 1);
        limpiar();
        FilaHoyDTO f = fila(hoyService.obtenerHoy(usuario, VIERNES, VIERNES).delDia().habitos(),
                veces.getHabitoId());
        assertEquals(1, f.completados());
        assertEquals(2, f.meta());
        assertFalse(f.hecho());
        assertTrue(f.completadoEnFecha());
        assertEquals(Unidad.VECES, f.unidad());
        assertNull(f.minutosHechos());
        assertNull(f.minutosObjetivo());
    }

    // ── Ánimo y ritmo ────────────────────────────────────────────

    @Test
    void comoVa_unDiaDeMinutosAMediasNoCompletaElDia() {
        Usuario usuario = usuario();
        Habito diario = habito(usuario, minutosDiario(30), LUNES);
        registro(diario, VIERNES, 10);
        limpiar();
        assertEquals(AvanceDeHoy.EMPEZADO, cumplimiento.comoVaHoy(usuario, VIERNES, MADRID));
        registro(em.find(Habito.class, diario.getHabitoId()), VIERNES, 20);
        limpiar();
        assertEquals(AvanceDeHoy.COMPLETO, cumplimiento.comoVaHoy(usuario, VIERNES, MADRID));
    }

    @Test
    void ritmo_diarioDeMinutos_cadaDiaCuentaEnProporcion() {
        Usuario usuario = usuario();
        Habito diario = habito(usuario, minutosDiario(30), LUNES);
        registro(diario, LUNES, 15);
        registro(diario, MARTES, 30);
        registro(diario, MIERCOLES, 45);
        limpiar();
        double esperado = CalculoRitmo.cerrarPeriodos(0.5, List.of(0.5, 1.0, 1.0), Frecuencia.DIARIO);
        assertEquals(esperado,
                ritmoService.ritmoVigente(em.find(Habito.class, diario.getHabitoId()), JUEVES), 1e-12);
    }

    @Test
    void ritmo_semanalDeMinutos_laSemanaEsLaSumaDeDiasEntreLaMeta() {
        Usuario usuario = usuario();
        Habito semanal = habito(usuario, new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, null), LUNES);
        registro(semanal, LUNES, 60);
        registro(semanal, MARTES, 30);
        registro(semanal, MIERCOLES, 90);
        limpiar();
        double esperado = CalculoRitmo.cerrarPeriodo(0.5, 2.5 / 3, Frecuencia.SEMANAL);
        assertEquals(esperado, ritmoService.ritmoVigente(em.find(Habito.class, semanal.getHabitoId()),
                LUNES.plusWeeks(1).plusDays(1)), 1e-12);
    }

    // ── Logros de constancia ─────────────────────────────────────

    @Test
    void constancia_soloCuentanLosDiasQueLleganASusMinutos() {
        Usuario usuario = usuario();
        Habito diario = habito(usuario, minutosDiario(30), LUNES);
        registro(diario, LUNES, 30);
        registro(diario, MARTES, 15);
        registro(diario, MARTES, 15);
        registro(diario, MIERCOLES, 29);
        limpiar();
        Habito leido = em.find(Habito.class, diario.getHabitoId());
        assertFalse(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, leido).contains("RACHA_3"),
                "dos días hechos y uno a medias no son tres");
        registro(leido, JUEVES, 30);
        limpiar();
        assertTrue(logrosHabitosService.evaluarTrasCompletarRegistro(usuario,
                em.find(Habito.class, diario.getHabitoId())).contains("RACHA_3"));
    }

    // ── Volumen (consulta de Postgres de verdad) ─────────────────

    @Test
    void volumen_enMinutosCuentaUnRegistroPorDia_enVecesCadaFila() {
        Usuario usuario = usuario();
        Habito veces = habito(usuario, new Objetivo(Frecuencia.DIARIO, 3, null), LUNES);
        registro(veces, LUNES, 1);
        registro(veces, LUNES, 1);
        registro(veces, LUNES, 1);                         // 3
        Habito minutos = habito(usuario, minutosDiario(30), LUNES);
        registro(minutos, LUNES, 10);
        registro(minutos, LUNES, 10);
        registro(minutos, LUNES, 10);
        registro(minutos, MARTES, 5);
        registro(minutos, MARTES, 5);                      // 2
        // De veces hasta el martes y de minutos desde el miércoles.
        Habito convertido = habito(usuario, new Objetivo(Frecuencia.DIARIO, 2, null), LUNES);
        em.persist(new HabitoObjetivo(convertido, MIERCOLES, minutosDiario(20)));
        registro(convertido, MARTES, 1);
        registro(convertido, MARTES, 1);                   // 2
        registro(convertido, MIERCOLES, 5);
        registro(convertido, MIERCOLES, 5);
        registro(convertido, MIERCOLES, 5);                // 1
        // Sin versiones: cuenta su unidad.
        Habito sinVersiones = new Habito("Sin versiones", null, Frecuencia.DIARIO, 1, usuario, null, INICIO);
        sinVersiones.setUnidad(Unidad.MINUTOS);
        sinVersiones.setMinutosObjetivo(15);
        em.persist(sinVersiones);
        registro(sinVersiones, JUEVES, 5);
        registro(sinVersiones, JUEVES, 5);                 // 1
        // Un registro anterior a la primera versión se juzga con la primera.
        Habito tardio = new Habito("Tardío", null, Frecuencia.DIARIO, 1, usuario, null, VIERNES);
        tardio.setUnidad(Unidad.MINUTOS);
        tardio.setMinutosObjetivo(15);
        em.persist(tardio);
        em.persist(new HabitoObjetivo(tardio, VIERNES, minutosDiario(15)));
        registro(tardio, JUEVES, 5);
        registro(tardio, JUEVES, 5);                       // 1
        // De otro usuario: no cuenta.
        registro(habito(usuario(), new Objetivo(Frecuencia.DIARIO, 1, null), LUNES), LUNES, 1);
        limpiar();

        assertEquals(10, registroDAO.contarPorUsuario(usuario.getUsuarioId()));
    }

    // ── Con el reloj real: día completo, /dashboard, /semana, /resumen, detalle ──

    @Test
    void conElRelojReal_lasLecturasDeLaAppYaInstaladaCuentanDiasHechos() {
        LocalDate hoy = LocalDate.now(MADRID);
        LocalDate otroDia = hoy.getDayOfMonth() > 1 ? hoy.minusDays(1) : hoy.plusDays(1);
        Usuario usuario = usuario();
        Habito diario = habito(usuario, minutosDiario(30), hoy.with(DayOfWeek.MONDAY));
        registro(diario, hoy, 10);
        limpiar();

        // A medias: el día no está completo y la fila no está hecha.
        assertFalse(habitoService.esDiaCompleto(usuario));
        DashboardHabitoDTO dash = habitoService.obtenerDashboard(usuario).get(0);
        assertEquals(0, dash.getCompletadosPeriodo());
        assertFalse(dash.isCompletadoHoy());
        assertEquals(List.of(), dash.getFechasCompletadas());
        SemanaDashboardDTO semana = habitoService.obtenerSemana(usuario, null);
        HabitoDiaDTO deHoy = semana.getDias().get(hoy.getDayOfWeek().getValue() - 1).getHabitos().get(0);
        assertFalse(deHoy.isCompletado());

        // Llega a los 30: todo pasa a hecho.
        Habito leido = em.find(Habito.class, diario.getHabitoId());
        Registro segundo = registro(leido, hoy, 20);
        segundo.setOrigen(OrigenRegistro.ENFOQUE);
        registro(leido, otroDia, 5);
        limpiar();
        assertTrue(habitoService.esDiaCompleto(usuario));
        dash = habitoService.obtenerDashboard(usuario).get(0);
        assertEquals(1, dash.getCompletadosPeriodo());
        assertTrue(dash.isCompletadoHoy());
        assertEquals(List.of(hoy.toString()), dash.getFechasCompletadas());
        semana = habitoService.obtenerSemana(usuario, null);
        assertTrue(semana.getDias().get(hoy.getDayOfWeek().getValue() - 1).getHabitos().get(0).isCompletado());

        HabitoResumenDTO resumen = habitoService.obtenerResumen(usuario).get(0);
        assertEquals(1, resumen.getTotalCompletados(), "un día hecho, no tres registros");
        assertEquals(Unidad.MINUTOS, resumen.getHabito().getUnidad());
        assertEquals(30, resumen.getHabito().getMinutosObjetivo());

        HabitoDetalleDTO detalle = habitoService.obtenerDetalle(diario.getHabitoId(), YearMonth.from(hoy));
        assertEquals(Unidad.MINUTOS, detalle.getUnidad());
        assertEquals(30, detalle.getMinutosObjetivo());
        assertEquals(35, detalle.getTotalMinutos());
        assertEquals(1, detalle.getTotalCompletados());
        assertEquals(1, detalle.getCompletadosMesActual());
        RegistroDiaDTO celdaHoy = detalle.getHeatmap().get(hoy.getDayOfMonth() - 1);
        assertTrue(celdaHoy.isCompletado());
        assertEquals(2, celdaHoy.getVeces());
        assertEquals(30, celdaHoy.getMinutos());
        RegistroDiaDTO celdaOtro = detalle.getHeatmap().get(otroDia.getDayOfMonth() - 1);
        assertFalse(celdaOtro.isCompletado(), "5 de 30 es un día a medias");
        assertEquals(5, celdaOtro.getMinutos());
        assertTrue(detalle.getUltimosRegistros().stream()
                .anyMatch(r -> r.getCantidad() == 20 && r.getOrigen() == OrigenRegistro.ENFOQUE));
    }

    @Test
    void conElRelojReal_elDetalleDeVecesNoTraeMinutos() {
        LocalDate hoy = LocalDate.now(MADRID);
        Usuario usuario = usuario();
        Habito veces = habito(usuario, new Objetivo(Frecuencia.DIARIO, 2, null), hoy);
        registro(veces, hoy, 1);
        limpiar();
        HabitoDetalleDTO detalle = habitoService.obtenerDetalle(veces.getHabitoId(), YearMonth.from(hoy));
        assertEquals(Unidad.VECES, detalle.getUnidad());
        assertNull(detalle.getMinutosObjetivo());
        assertNull(detalle.getTotalMinutos());
        assertNull(detalle.getHeatmap().get(hoy.getDayOfMonth() - 1).getMinutos());
        assertTrue(detalle.getHeatmap().get(hoy.getDayOfMonth() - 1).isCompletado(),
                "en veces una celda con algo sigue marcada, como antes");
        assertEquals(1, habitoService.obtenerResumen(usuario).get(0).getTotalCompletados());
    }
}
