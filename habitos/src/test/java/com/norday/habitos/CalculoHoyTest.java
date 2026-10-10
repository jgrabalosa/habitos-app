package com.norday.habitos;

import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Semana de referencia: lunes 5-oct-2026 a domingo 11-oct-2026.
 * Días fijos en ISO: 1 = lunes, 3 = miércoles, 5 = viernes.
 */
class CalculoHoyTest {

    private static final double D = 0.000001;
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 10);

    private static Habito habito(int id, Frecuencia frecuencia, int meta, String diasSemana) {
        Habito habito = new Habito();
        habito.setHabitoId(id);
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
        return habito;
    }

    private static Habito diario(int meta) {
        return habito(1, Frecuencia.DIARIO, meta, null);
    }

    private static Habito flexible(int meta) {
        return habito(2, Frecuencia.SEMANAL, meta, null);
    }

    /** Lunes, miércoles y viernes. */
    private static Habito fijo() {
        return habito(3, Frecuencia.SEMANAL, 3, "1,3,5");
    }

    @Test
    void diasFijos_leeLosDiasIso_yToleraEspaciosYBasura() {
        assertEquals(Set.of(1, 3, 5), CalculoHoy.diasFijos(habito(3, Frecuencia.SEMANAL, 3, "1, 3,5")));
        assertEquals(Set.of(2, 4), CalculoHoy.diasFijos(habito(3, Frecuencia.SEMANAL, 2, "2,,x,4")));
        assertTrue(CalculoHoy.diasFijos(flexible(3)).isEmpty());
        assertTrue(CalculoHoy.diasFijos(habito(3, Frecuencia.SEMANAL, 3, "  ")).isEmpty());
        assertTrue(CalculoHoy.diasFijos(habito(1, Frecuencia.DIARIO, 1, "1,3")).isEmpty());
    }

    @Test
    void tocaEl_unDiarioTocaTodosLosDias() {
        assertTrue(CalculoHoy.tocaEl(diario(1), LUNES));
        assertTrue(CalculoHoy.tocaEl(diario(4), SABADO));
    }

    @Test
    void tocaEl_unSemanalConDiasFijosSoloSusDias() {
        assertTrue(CalculoHoy.tocaEl(fijo(), LUNES));
        assertFalse(CalculoHoy.tocaEl(fijo(), MARTES));
        assertTrue(CalculoHoy.tocaEl(fijo(), MIERCOLES));
        assertFalse(CalculoHoy.tocaEl(fijo(), SABADO));
    }

    @Test
    void tocaEl_unFlexibleNoTocaNingunDia() {
        assertFalse(CalculoHoy.tocaEl(flexible(3), LUNES));
        assertFalse(CalculoHoy.tocaEl(flexible(3), SABADO));
    }

    @Test
    void tocaEl_nadaTocaAntesDeExistir() {
        Habito creadoElMiercoles = diario(1);
        creadoElMiercoles.setFechaInicio(MIERCOLES);

        assertFalse(CalculoHoy.tocaEl(creadoElMiercoles, MARTES));
        assertTrue(CalculoHoy.tocaEl(creadoElMiercoles, MIERCOLES));
        assertTrue(CalculoHoy.tocaEl(creadoElMiercoles, JUEVES));
    }

    @Test
    void vaEnLaSemana_elFlexibleSiempre_elFijoFueraDeSuDia_elDiarioNunca() {
        assertTrue(CalculoHoy.vaEnLaSemanaDe(flexible(3), LUNES));
        assertTrue(CalculoHoy.vaEnLaSemanaDe(flexible(3), SABADO));
        assertTrue(CalculoHoy.vaEnLaSemanaDe(fijo(), MARTES));
        assertFalse(CalculoHoy.vaEnLaSemanaDe(fijo(), MIERCOLES));
        assertFalse(CalculoHoy.vaEnLaSemanaDe(diario(1), MARTES));
    }

    @Test
    void vaEnLaSemana_unSemanalCreadoDespuesDeEsaSemanaNoSale() {
        Habito creadoLaSemanaSiguiente = flexible(3);
        creadoLaSemanaSiguiente.setFechaInicio(LocalDate.of(2026, 10, 12));
        Habito creadoElViernes = flexible(3);
        creadoElViernes.setFechaInicio(VIERNES);

        assertFalse(CalculoHoy.vaEnLaSemanaDe(creadoLaSemanaSiguiente, MIERCOLES));
        assertTrue(CalculoHoy.vaEnLaSemanaDe(creadoElViernes, MIERCOLES));
    }

    @Test
    void cubierto_siSeHizoEseDia() {
        assertTrue(CalculoHoy.cubierto(fijo(), List.of(MIERCOLES), MIERCOLES));
        assertFalse(CalculoHoy.cubierto(fijo(), List.of(), LUNES));
    }

    @Test
    void cubierto_recuperarNoCubreElDiaSiguiente() {
        // Falló el lunes y lo hizo el martes: el miércoles lleva una de dos.
        assertFalse(CalculoHoy.cubierto(fijo(), List.of(MARTES), MIERCOLES));
    }

    @Test
    void cubierto_adelantarCubre() {
        // Lunes hecho y martes adelantado: el miércoles ya está cubierto.
        assertTrue(CalculoHoy.cubierto(fijo(), List.of(LUNES, MARTES), MIERCOLES));

        // Miércoles y viernes fijos; hecho el martes: el miércoles está cubierto.
        Habito miercolesYViernes = habito(3, Frecuencia.SEMANAL, 2, "3,5");
        assertTrue(CalculoHoy.cubierto(miercolesYViernes, List.of(MARTES), MIERCOLES));
    }

    @Test
    void cubierto_loHechoDespuesNoRellenaUnDiaPasado() {
        List<LocalDate> juevesYViernes = List.of(JUEVES, VIERNES);

        assertFalse(CalculoHoy.cubierto(fijo(), juevesYViernes, LUNES));
        assertFalse(CalculoHoy.cubierto(fijo(), juevesYViernes, MIERCOLES));
        assertTrue(CalculoHoy.cubierto(fijo(), juevesYViernes, VIERNES));
    }

    @Test
    void cubierto_unHabitoCreadoAMitadDeSemanaSoloCuentaSusDias() {
        // Creado el miércoles: el lunes no le tocaba. Hecho miércoles y jueves.
        Habito creadoElMiercoles = fijo();
        creadoElMiercoles.setFechaInicio(MIERCOLES);

        assertTrue(CalculoHoy.cubierto(creadoElMiercoles, List.of(MIERCOLES, JUEVES), VIERNES));
        assertFalse(CalculoHoy.cubierto(fijo(), List.of(MIERCOLES, JUEVES), VIERNES));
    }

    @Test
    void hechoEnElDia_unDiarioNecesitaLlegarASuObjetivo() {
        assertFalse(CalculoHoy.hechoEnElDia(diario(4), List.of(SABADO, SABADO, SABADO), SABADO));
        assertTrue(CalculoHoy.hechoEnElDia(diario(4), List.of(SABADO, SABADO, SABADO, SABADO), SABADO));
        assertTrue(CalculoHoy.hechoEnElDia(diario(1), List.of(VIERNES, SABADO), SABADO));
        assertFalse(CalculoHoy.hechoEnElDia(diario(1), List.of(VIERNES), SABADO));
    }

    @Test
    void hechoEnLaSemana_alLlegarAlObjetivoSemanal() {
        assertFalse(CalculoHoy.hechoEnLaSemana(flexible(3), List.of(LUNES, MARTES)));
        assertTrue(CalculoHoy.hechoEnLaSemana(flexible(3), List.of(LUNES, MARTES, JUEVES)));
        assertTrue(CalculoHoy.hechoEnLaSemana(flexible(1), List.of(LUNES, MARTES)));
    }

    @Test
    void creditoDelDia_unDiarioEnProporcion_yUnFijoTodoONada() {
        assertEquals(0.75, CalculoHoy.creditoDelDia(diario(4), List.of(SABADO, SABADO, SABADO), SABADO), D);
        assertEquals(1.0, CalculoHoy.creditoDelDia(diario(1), List.of(SABADO, SABADO), SABADO), D);
        assertEquals(0.0, CalculoHoy.creditoDelDia(fijo(), List.of(MARTES), MIERCOLES), D);
        assertEquals(1.0, CalculoHoy.creditoDelDia(fijo(), List.of(MIERCOLES), MIERCOLES), D);
    }

    @Test
    void avanceDelDia_elEjemploDeLaMaqueta() {
        // Sábado 10. Agua 3/4, leer 0/1, yoga (martes, jueves y sábado) con
        // una sesión el martes, y meditar hecho. El gimnasio es flexible: no
        // cuenta para el día.
        Habito agua = habito(1, Frecuencia.DIARIO, 4, null);
        Habito leer = habito(2, Frecuencia.DIARIO, 1, null);
        Habito yoga = habito(3, Frecuencia.SEMANAL, 3, "2,4,6");
        Habito meditar = habito(4, Frecuencia.DIARIO, 1, null);
        Habito gimnasio = habito(5, Frecuencia.SEMANAL, 5, null);
        Map<Integer, List<LocalDate>> fechas = Map.of(
                1, List.of(SABADO, SABADO, SABADO),
                3, List.of(MARTES),
                4, List.of(SABADO),
                5, List.of(LUNES, MIERCOLES));

        double avance = CalculoHoy.avanceDelDia(
                List.of(agua, leer, yoga, meditar, gimnasio), fechas, SABADO).orElseThrow();

        // (0,75 + 0 + 0 + 1) / 4
        assertEquals(0.4375, avance, D);
    }

    @Test
    void avanceDelDia_siNoTocabaNada_noHayAvance() {
        assertTrue(CalculoHoy.avanceDelDia(List.of(flexible(3)), Map.of(), SABADO).isEmpty());
        assertTrue(CalculoHoy.avanceDelDia(List.of(), Map.of(), SABADO).isEmpty());
    }
}
