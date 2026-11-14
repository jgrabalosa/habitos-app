package com.norday.habitos;

import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.CalculoPremio.Premio;
import com.norday.habitos.model.Frecuencia;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Test dorado: las cifras de los hábitos de VECES tal como eran antes de los
 * hábitos en minutos (Fase 3). Se escribió y pasó contra el CalculoPremio
 * anterior a la fase; si falla, lo que ha cambiado es el premio de VECES, y
 * eso no se permite. No se ajustan las cifras.
 *
 * Cada fila: frecuencia, meta, hechos antes del paso, y el premio
 * (puntos, xp, puntosExtra, xpExtra).
 */
class PremioVecesDoradoTest {

    private static final Object[][] CASOS = {
            // Diario: el día (25 / 5) repartido entre sus veces
            {Frecuencia.DIARIO, 1, 0, 25, 5, 0, 0},
            {Frecuencia.DIARIO, 1, 1, 0, 0, 0, 0},
            {Frecuencia.DIARIO, 2, 0, 12, 2, 0, 0},
            {Frecuencia.DIARIO, 2, 1, 13, 3, 0, 0},
            {Frecuencia.DIARIO, 3, 0, 8, 1, 0, 0},
            {Frecuencia.DIARIO, 3, 1, 8, 2, 0, 0},
            {Frecuencia.DIARIO, 3, 2, 9, 2, 0, 0},
            {Frecuencia.DIARIO, 3, 3, 0, 0, 0, 0},
            {Frecuencia.DIARIO, 4, 0, 6, 1, 0, 0},
            {Frecuencia.DIARIO, 4, 3, 7, 2, 0, 0},
            {Frecuencia.DIARIO, 5, 0, 5, 1, 0, 0},
            {Frecuencia.DIARIO, 5, 4, 5, 1, 0, 0},
            {Frecuencia.DIARIO, 7, 0, 3, 0, 0, 0},
            {Frecuencia.DIARIO, 7, 1, 4, 1, 0, 0},
            {Frecuencia.DIARIO, 7, 2, 3, 1, 0, 0},
            {Frecuencia.DIARIO, 7, 3, 4, 0, 0, 0},
            {Frecuencia.DIARIO, 7, 6, 4, 1, 0, 0},
            {Frecuencia.DIARIO, 0, 0, 25, 5, 0, 0},   // meta 0 se trata como 1
            {Frecuencia.DIARIO, 3, -1, 8, 1, 0, 0},   // hechos negativos se tratan como 0
            // Semanal: cada sesión un día entero; la que completa la semana, +20 %
            {Frecuencia.SEMANAL, 1, 0, 25, 5, 5, 1},
            {Frecuencia.SEMANAL, 1, 1, 0, 0, 0, 0},
            {Frecuencia.SEMANAL, 2, 0, 25, 5, 0, 0},
            {Frecuencia.SEMANAL, 2, 1, 25, 5, 10, 2},
            {Frecuencia.SEMANAL, 3, 0, 25, 5, 0, 0},
            {Frecuencia.SEMANAL, 3, 1, 25, 5, 0, 0},
            {Frecuencia.SEMANAL, 3, 2, 25, 5, 15, 3},
            {Frecuencia.SEMANAL, 3, 3, 0, 0, 0, 0},
            {Frecuencia.SEMANAL, 4, 3, 25, 5, 20, 4},
            {Frecuencia.SEMANAL, 5, 4, 25, 5, 25, 5},
            {Frecuencia.SEMANAL, 7, 6, 25, 5, 35, 7},
            {Frecuencia.SEMANAL, 7, 7, 0, 0, 0, 0},
    };

    @Test
    void vecesConservaLasCifrasDeAntesDeLosMinutos() {
        for (Object[] caso : CASOS) {
            Frecuencia frecuencia = (Frecuencia) caso[0];
            int meta = (int) caso[1];
            int antes = (int) caso[2];
            Premio esperado = new Premio((int) caso[3], (int) caso[4], (int) caso[5], (int) caso[6]);
            assertEquals(esperado, CalculoPremio.delPaso(frecuencia, antes, meta),
                    frecuencia + " meta " + meta + " con " + antes + " hechos antes");
        }
    }

    @Test
    void vecesConservaLosDiasHechosDeAntes() {
        java.time.LocalDate lunes = java.time.LocalDate.of(2026, 10, 5);
        java.util.List<java.time.LocalDate> fechas = java.util.List.of(
                lunes, lunes, lunes, lunes.plusDays(1), lunes.plusDays(2), lunes.plusDays(2));
        // Diario meta 2: lunes (3) y miércoles (2) llegan; martes (1) no.
        assertEquals(2, CalculoPremio.diasHechos(Frecuencia.DIARIO, 2, fechas));
        // Diario meta 3: solo el lunes.
        assertEquals(1, CalculoPremio.diasHechos(Frecuencia.DIARIO, 3, fechas));
        // Semanal: días distintos con alguna sesión.
        assertEquals(3, CalculoPremio.diasHechos(Frecuencia.SEMANAL, 3, fechas));
    }
}
