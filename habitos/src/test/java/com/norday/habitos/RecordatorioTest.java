package com.norday.habitos;

import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cuándo toca mandar el recordatorio de un hábito: sección 16 de las
 * decisiones del rediseño. "Hoy" es el sábado 10-oct-2026 y el lunes de esa
 * semana, el 5.
 */
class RecordatorioTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    private static Habito habito(Frecuencia frecuencia, int meta, String diasSemana) {
        Habito habito = new Habito();
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
        return habito;
    }

    private final Habito leer = habito(Frecuencia.DIARIO, 1, null);
    private final Habito agua = habito(Frecuencia.DIARIO, 4, null);
    private final Habito gimnasio = habito(Frecuencia.SEMANAL, 3, null);
    private final Habito yoga = habito(Frecuencia.SEMANAL, 3, "2,4,6");

    @Test
    void unDiario_seRecuerdaMientrasHoyNoLlegueASuObjetivo() {
        assertTrue(CalculoHoy.tocaRecordar(leer, List.of(), HOY));
        assertTrue(CalculoHoy.tocaRecordar(leer, List.of(VIERNES), HOY));
        assertFalse(CalculoHoy.tocaRecordar(leer, List.of(HOY), HOY));

        // Tres vasos de cuatro: aún falta uno.
        assertTrue(CalculoHoy.tocaRecordar(agua, List.of(HOY, HOY, HOY), HOY));
        assertFalse(CalculoHoy.tocaRecordar(agua, List.of(HOY, HOY, HOY, HOY), HOY));
    }

    @Test
    void unSemanalFlexible_seRecuerdaMientrasFaltenVecesEsaSemana() {
        assertTrue(CalculoHoy.tocaRecordar(gimnasio, List.of(), HOY));
        assertTrue(CalculoHoy.tocaRecordar(gimnasio, List.of(LUNES, MARTES), HOY));
        // Con el objetivo de la semana cumplido se calla hasta el lunes.
        assertFalse(CalculoHoy.tocaRecordar(gimnasio, List.of(LUNES, MARTES, JUEVES), HOY));
    }

    @Test
    void unSemanalFlexible_noSeRecuerdaElDiaEnQueYaSeHaHecho() {
        // Lleva dos de tres, pero una es la de hoy.
        assertFalse(CalculoHoy.tocaRecordar(gimnasio, List.of(LUNES, HOY), HOY));
    }

    @Test
    void unSemanalConDiasFijos_soloAvisaSusDias() {
        // Yoga toca martes, jueves y sábado.
        assertTrue(CalculoHoy.tocaRecordar(yoga, List.of(MARTES), HOY));
        assertFalse(CalculoHoy.tocaRecordar(yoga, List.of(HOY), HOY));
        // El viernes no es su día: no avisa aunque no lleve ninguna.
        assertFalse(CalculoHoy.tocaRecordar(yoga, List.of(), VIERNES));
    }

    @Test
    void unSemanalConDiasFijosYaAdelantado_noAvisa() {
        // Tres sesiones entre lunes y miércoles cubren las tres de la semana.
        assertFalse(CalculoHoy.tocaRecordar(yoga, List.of(LUNES, MARTES, MIERCOLES), HOY));
    }

    @Test
    void unHabitoQueEmpiezaManana_noSeRecuerdaHoy() {
        leer.setFechaInicio(HOY.plusDays(1));

        assertFalse(CalculoHoy.tocaRecordar(leer, List.of(), HOY));
    }
}
