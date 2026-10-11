package com.norday.habitos;

import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.CalculoPremio.Premio;
import com.norday.habitos.model.Frecuencia;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las cifras esperadas NO salen de CalculoPremio: salen de la sección 11 de
 * las decisiones del rediseño (25 puntos y 5 de XP por día de hábito, un
 * 20 % más al completar la semana) y de una cuenta hecha aparte. Si un test
 * falla, no se ajusta la cifra.
 */
class CalculoPremioTest {

    private static Premio diario(int hechosAntes, int objetivo) {
        return CalculoPremio.delPaso(Frecuencia.DIARIO, hechosAntes, objetivo);
    }

    private static Premio semanal(int hechosAntes, int objetivo) {
        return CalculoPremio.delPaso(Frecuencia.SEMANAL, hechosAntes, objetivo);
    }

    @Test
    void diario_conObjetivo1_cobraElDiaEntero() {
        assertEquals(new Premio(25, 5, 0, 0), diario(0, 1));
    }

    @Test
    void diario_conObjetivo4_reparteElDiaEntreSusVeces() {
        assertEquals(new Premio(6, 1, 0, 0), diario(0, 4));
        assertEquals(new Premio(6, 1, 0, 0), diario(1, 4));
        assertEquals(new Premio(6, 1, 0, 0), diario(2, 4));
        assertEquals(new Premio(7, 2, 0, 0), diario(3, 4));
    }

    @Test
    void diario_quedarseEnTresDeCuatro_cobraDieciocho() {
        int puntos = diario(0, 4).puntosTotales() + diario(1, 4).puntosTotales()
                + diario(2, 4).puntosTotales();
        int xp = diario(0, 4).xpTotal() + diario(1, 4).xpTotal() + diario(2, 4).xpTotal();
        assertEquals(18, puntos);
        assertEquals(3, xp);
    }

    @Test
    void diario_conObjetivo6_elPrimerPasoDaPuntosAunqueNoDeXp() {
        assertEquals(new Premio(4, 0, 0, 0), diario(0, 6));
        assertEquals(new Premio(4, 1, 0, 0), diario(1, 6));
        assertEquals(new Premio(4, 1, 0, 0), diario(2, 6));
        assertEquals(new Premio(4, 1, 0, 0), diario(3, 6));
        assertEquals(new Premio(4, 1, 0, 0), diario(4, 6));
        assertEquals(new Premio(5, 1, 0, 0), diario(5, 6));
    }

    @Test
    void diario_conCualquierObjetivo_elDiaCompletoSumaLoMismoQueHoy() {
        for (int objetivo = 1; objetivo <= 60; objetivo++) {
            int puntos = 0;
            int xp = 0;
            for (int hechosAntes = 0; hechosAntes < objetivo; hechosAntes++) {
                Premio premio = diario(hechosAntes, objetivo);
                assertTrue(premio.puntos() >= 0, "puntos negativos con objetivo " + objetivo);
                assertTrue(premio.xp() >= 0, "xp negativa con objetivo " + objetivo);
                assertEquals(0, premio.puntosExtra(), "un diario no lleva extra");
                assertEquals(0, premio.xpExtra(), "un diario no lleva extra");
                puntos += premio.puntos();
                xp += premio.xp();
            }
            assertEquals(25, puntos, "puntos del día con objetivo " + objetivo);
            assertEquals(5, xp, "xp del día con objetivo " + objetivo);
        }
    }

    @Test
    void semanal_cadaSesionCobraUnDiaEntero() {
        assertEquals(new Premio(25, 5, 0, 0), semanal(0, 5));
        assertEquals(new Premio(25, 5, 0, 0), semanal(1, 5));
        assertEquals(new Premio(25, 5, 0, 0), semanal(2, 5));
        assertEquals(new Premio(25, 5, 0, 0), semanal(3, 5));
    }

    @Test
    void semanal_elGimnasioDeCinco_sumaCientoCincuentaYTreintaEnLaSemana() {
        assertEquals(new Premio(25, 5, 25, 5), semanal(4, 5));

        int puntos = 0;
        int xp = 0;
        for (int hechosAntes = 0; hechosAntes < 5; hechosAntes++) {
            puntos += semanal(hechosAntes, 5).puntosTotales();
            xp += semanal(hechosAntes, 5).xpTotal();
        }
        assertEquals(150, puntos);
        assertEquals(30, xp);
    }

    @Test
    void semanal_elExtraEsUnVeintePorCientoDeLoGanadoEsaSemana() {
        assertEquals(new Premio(25, 5, 15, 3), semanal(2, 3));
        assertEquals(new Premio(25, 5, 5, 1), semanal(0, 1));
        assertEquals(new Premio(25, 5, 35, 7), semanal(6, 7));
    }

    @Test
    void pasarseDelObjetivo_noDaNada() {
        assertEquals(Premio.NADA, semanal(5, 5));
        assertEquals(Premio.NADA, semanal(6, 5));
        assertEquals(Premio.NADA, diario(4, 4));
        assertEquals(Premio.NADA, diario(1, 1));
    }

    @Test
    void unObjetivoMenorQueUno_seTrataComoUno() {
        assertEquals(new Premio(25, 5, 0, 0), diario(0, 0));
        assertEquals(new Premio(25, 5, 5, 1), semanal(0, 0));
        assertEquals(Premio.NADA, diario(1, 0));
    }
}
