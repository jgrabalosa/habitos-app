package com.norday.habitos;

import com.norday.habitos.model.CalculoObjetivo;
import com.norday.habitos.model.CalculoObjetivo.PeriodoDelCambio;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Objetivo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La regla de cambios de objetivo: un cambio nunca reinterpreta un periodo
 * con registros. Hoy es el viernes 9-oct-2026; la semana empieza el lunes 5.
 */
class CalculoObjetivoTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 10);
    private static final LocalDate LUNES_SIGUIENTE = LocalDate.of(2026, 10, 12);

    private static final Objetivo DIARIO_1 = new Objetivo(Frecuencia.DIARIO, 1, null);
    private static final Objetivo DIARIO_2 = new Objetivo(Frecuencia.DIARIO, 2, null);
    private static final Objetivo SEMANAL_3 = new Objetivo(Frecuencia.SEMANAL, 3, null);
    private static final Objetivo SEMANAL_4 = new Objetivo(Frecuencia.SEMANAL, 4, null);
    private static final Objetivo SEMANAL_L_X_V = new Objetivo(Frecuencia.SEMANAL, 3, "1,3,5");
    private static final Objetivo SEMANAL_M_J_S = new Objetivo(Frecuencia.SEMANAL, 3, "2,4,6");

    private static LocalDate efecto(Objetivo actual, Objetivo nuevo, LocalDate... registros) {
        return CalculoObjetivo.fechaDeEfecto(actual, nuevo, VIERNES, INICIO, List.of(registros));
    }

    @Test
    void elPeriodoDelCambio_esElDiaSoloEnUnDiarioQueSigueDiario() {
        assertEquals(PeriodoDelCambio.NINGUNO, CalculoObjetivo.periodoDelCambio(DIARIO_1, DIARIO_1));
        assertEquals(PeriodoDelCambio.DIA, CalculoObjetivo.periodoDelCambio(DIARIO_1, DIARIO_2));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(SEMANAL_3, SEMANAL_4));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(SEMANAL_L_X_V, SEMANAL_M_J_S));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(DIARIO_1, SEMANAL_3));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(SEMANAL_3, DIARIO_1));
    }

    @Test
    void sinCambio_noHayFechaDeEfecto() {
        assertNull(efecto(DIARIO_1, DIARIO_1, VIERNES));
        assertNull(efecto(SEMANAL_3, new Objetivo(Frecuencia.SEMANAL, 3, ""), VIERNES));
    }

    @Test
    void metaDeUnDiario_conRegistrosHoy_manana_sinRegistrosHoy_hoy() {
        assertEquals(SABADO, efecto(DIARIO_1, DIARIO_2, VIERNES));
        assertEquals(VIERNES, efecto(DIARIO_1, DIARIO_2, LUNES, LUNES.plusDays(3)));
        assertEquals(VIERNES, efecto(DIARIO_2, DIARIO_1));
    }

    @Test
    void diasPorSemana_conRegistrosEstaSemana_lunesSiguiente_sinEllos_elLunesDeEstaSemana() {
        // Sin registros esta semana, la semana entera pasa a la regla nueva.
        assertEquals(LUNES_SIGUIENTE, efecto(SEMANAL_3, SEMANAL_4, LUNES));
        assertEquals(LUNES, efecto(SEMANAL_3, SEMANAL_4, LUNES.minusDays(1), LUNES.minusDays(4)));
        assertEquals(LUNES, efecto(SEMANAL_4, SEMANAL_3));
    }

    @Test
    void diasFijos_conRegistrosEstaSemana_lunesSiguiente() {
        assertEquals(LUNES_SIGUIENTE, efecto(SEMANAL_L_X_V, SEMANAL_M_J_S, VIERNES));
        assertEquals(LUNES, efecto(SEMANAL_L_X_V, SEMANAL_M_J_S));
    }

    @Test
    void frecuencia_vaPorSemanas_aunqueHoyNoHayaRegistros() {
        assertEquals(LUNES_SIGUIENTE, efecto(DIARIO_1, SEMANAL_3, LUNES));
        assertEquals(LUNES_SIGUIENTE, efecto(SEMANAL_3, DIARIO_1, LUNES.plusDays(2)));
        assertEquals(LUNES, efecto(DIARIO_1, SEMANAL_3, LUNES.minusDays(1)));
        assertEquals(LUNES, efecto(SEMANAL_3, DIARIO_1, LUNES.minusDays(1)));
    }

    @Test
    void unHabitoSinNingunRegistro_aplicaCualquierCambioAlMomento() {
        // Al momento: la meta de un diario desde hoy; lo semanal, desde el
        // lunes de esta semana.
        assertEquals(VIERNES, efecto(DIARIO_1, DIARIO_2));
        assertEquals(LUNES, efecto(DIARIO_1, SEMANAL_3));
        assertEquals(LUNES, efecto(SEMANAL_L_X_V, SEMANAL_M_J_S));
    }

    @Test
    void unCambioSemanalSinRegistros_noEmpiezaAntesDeLaFechaDeInicio() {
        // Creado el miércoles: la semana empieza para él el miércoles.
        LocalDate miercoles = LUNES.plusDays(2);
        assertEquals(miercoles,
                CalculoObjetivo.fechaDeEfecto(DIARIO_1, SEMANAL_3, VIERNES, miercoles, List.of()));
    }

    @Test
    void unHabitoQueAunNoHaEmpezado_cambiaDesdeSuFechaDeInicio() {
        LocalDate dentroDeUnaSemana = VIERNES.plusDays(7);
        assertEquals(dentroDeUnaSemana,
                CalculoObjetivo.fechaDeEfecto(DIARIO_1, DIARIO_2, VIERNES, dentroDeUnaSemana, List.of()));
    }
}
