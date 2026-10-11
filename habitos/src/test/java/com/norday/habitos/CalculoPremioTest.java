package com.norday.habitos;

import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.CalculoPremio.Premio;
import com.norday.habitos.model.Frecuencia;
import java.time.LocalDate;
import java.util.List;
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

    // Hitos: las cifras salen de la sección 14 de las decisiones del rediseño
    // (3, 7, 30, 100 y 365 días hechos; 50, 100, 300, 750 y 2000 puntos).

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);

    @Test
    void hitos_seCobraAlLlegarACadaUno() {
        assertEquals(50, CalculoPremio.puntosDeHitos(2, 3));
        assertEquals(100, CalculoPremio.puntosDeHitos(6, 7));
        assertEquals(300, CalculoPremio.puntosDeHitos(29, 30));
        assertEquals(750, CalculoPremio.puntosDeHitos(99, 100));
        assertEquals(2000, CalculoPremio.puntosDeHitos(364, 365));
    }

    @Test
    void hitos_entreUnoYOtroOSinAvanzar_noSeCobraNada() {
        assertEquals(0, CalculoPremio.puntosDeHitos(0, 1));
        assertEquals(0, CalculoPremio.puntosDeHitos(3, 4));
        assertEquals(0, CalculoPremio.puntosDeHitos(8, 9));
        assertEquals(0, CalculoPremio.puntosDeHitos(365, 366));
        assertEquals(0, CalculoPremio.puntosDeHitos(3, 3));
        assertEquals(0, CalculoPremio.puntosDeHitos(7, 6));
    }

    @Test
    void hitos_siElContadorSalta_seCobranTodosLosCruzados() {
        assertEquals(150, CalculoPremio.puntosDeHitos(2, 7));
        assertEquals(3200, CalculoPremio.puntosDeHitos(0, 365));
    }

    @Test
    void diasHechos_enUnDiarioCuentanLosDiasQueLlegaronAlObjetivo() {
        // Lunes 4 veces, martes 3 y miércoles 4.
        List<LocalDate> fechas = List.of(
                LUNES, LUNES, LUNES, LUNES,
                MARTES, MARTES, MARTES,
                MIERCOLES, MIERCOLES, MIERCOLES, MIERCOLES);

        assertEquals(2, CalculoPremio.diasHechos(Frecuencia.DIARIO, 4, fechas));
        // Hueco conocido y aceptado: con otro objetivo, los mismos registros
        // dan otros días hechos.
        assertEquals(3, CalculoPremio.diasHechos(Frecuencia.DIARIO, 2, fechas));
        assertEquals(3, CalculoPremio.diasHechos(Frecuencia.DIARIO, 1, List.of(LUNES, MARTES, MIERCOLES)));
    }

    @Test
    void diasHechos_enUnSemanalCadaSesionEsUnDia() {
        List<LocalDate> fechas = List.of(LUNES, MARTES, MIERCOLES);

        assertEquals(3, CalculoPremio.diasHechos(Frecuencia.SEMANAL, 5, fechas));
        assertEquals(3, CalculoPremio.diasHechos(Frecuencia.SEMANAL, 1, fechas));
    }

    @Test
    void diasHechos_sinRegistrosEsCero_yUnObjetivoMenorQueUnoSeTrataComoUno() {
        assertEquals(0, CalculoPremio.diasHechos(Frecuencia.DIARIO, 3, List.of()));
        assertEquals(0, CalculoPremio.diasHechos(Frecuencia.SEMANAL, 3, List.of()));
        assertEquals(2, CalculoPremio.diasHechos(Frecuencia.DIARIO, 0, List.of(LUNES, MARTES)));
    }
}
