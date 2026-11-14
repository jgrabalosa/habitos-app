package com.norday.habitos;

import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.CalculoPremio.DiaDelPeriodo;
import com.norday.habitos.model.CalculoPremio.Premio;
import com.norday.habitos.model.Frecuencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * El premio de los pasos de minutos. Las cifras NO salen de CalculoPremio:
 * salen de la sección 2 del diseño (un día vale 25 puntos y 5 XP, repartidos
 * en proporción a los minutos dentro del objetivo; en semanal, el día que
 * completa la semana añade un 20 %) y de una cuenta hecha aparte:
 * acumulado(total, k, n) = total·k/n en enteros, y cada paso cobra
 * acumulado(después) − acumulado(antes), con el acumulado limitado al
 * objetivo. Si un test falla, no se ajusta la cifra.
 */
class PremioMinutosTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);

    /** Un periodo que se va llenando paso a paso, como en la vida real. */
    private static final class Periodo {
        private final Frecuencia frecuencia;
        private final int meta;
        private final Map<LocalDate, DiaDelPeriodo> dias = new HashMap<>();

        Periodo(Frecuencia frecuencia, int meta) {
            this.frecuencia = frecuencia;
            this.meta = meta;
        }

        Premio paso(LocalDate fecha, int objetivo, int minutos) {
            Premio premio = CalculoPremio.delPasoEnMinutos(frecuencia, meta, Map.copyOf(dias), fecha, objetivo, minutos);
            DiaDelPeriodo antes = dias.getOrDefault(fecha, new DiaDelPeriodo(0, objetivo));
            dias.put(fecha, new DiaDelPeriodo(antes.hechos() + minutos, objetivo));
            return premio;
        }
    }

    private static Premio p(int puntos, int xp, int puntosExtra, int xpExtra) {
        return new Premio(puntos, xp, puntosExtra, xpExtra);
    }

    @Test
    void diario_quinceDeTreinta_esLaMitad_yElRestoLaOtra() {
        Periodo dia = new Periodo(Frecuencia.DIARIO, 1);
        assertEquals(p(12, 2, 0, 0), dia.paso(LUNES, 30, 15));
        assertEquals(p(13, 3, 0, 0), dia.paso(LUNES, 30, 15));
        assertEquals(p(0, 0, 0, 0), dia.paso(LUNES, 30, 10), "pasarse no da nada");
    }

    @Test
    void diario_tresPasosDeDiez_danLoMismoQueUnDiarioDeVecesDeMeta3() {
        Periodo dia = new Periodo(Frecuencia.DIARIO, 1);
        assertEquals(p(8, 1, 0, 0), dia.paso(LUNES, 30, 10));
        assertEquals(p(8, 2, 0, 0), dia.paso(LUNES, 30, 10));
        assertEquals(p(9, 2, 0, 0), dia.paso(LUNES, 30, 10));
    }

    @Test
    void diario_deUnaVezPasandose_cobraElDiaEntero() {
        assertEquals(p(25, 5, 0, 0), new Periodo(Frecuencia.DIARIO, 1).paso(LUNES, 30, 45));
    }

    @Test
    void diario_unMinutoDeSeiscientos_noDaNada_yLosSeiscientosSumanElDia() {
        Periodo dia = new Periodo(Frecuencia.DIARIO, 1);
        assertEquals(p(0, 0, 0, 0), dia.paso(LUNES, 600, 1));
        assertEquals(p(25, 5, 0, 0), dia.paso(LUNES, 600, 599));
    }

    @Test
    void diario_conPasosDeUnMinuto_daLasMismasCifrasQueVeces() {
        for (int objetivo = 1; objetivo <= 60; objetivo++) {
            Periodo dia = new Periodo(Frecuencia.DIARIO, 1);
            int puntos = 0;
            int xp = 0;
            for (int antes = 0; antes < objetivo; antes++) {
                Premio minutos = dia.paso(LUNES, objetivo, 1);
                assertEquals(CalculoPremio.delPaso(Frecuencia.DIARIO, antes, objetivo), minutos,
                        "objetivo " + objetivo + ", paso " + (antes + 1));
                puntos += minutos.puntos();
                xp += minutos.xp();
            }
            assertEquals(25, puntos);
            assertEquals(5, xp);
            assertEquals(Premio.NADA, dia.paso(LUNES, objetivo, 1));
        }
    }

    @Test
    void diario_cualquierRepartoSumaVeinticincoYCinco() {
        int[][] repartos = {{7, 7, 7, 9}, {1, 28, 1}, {29, 1}, {3, 3, 3, 3, 3, 3, 3, 3, 3, 3}, {13, 17, 40}};
        for (int[] reparto : repartos) {
            Periodo dia = new Periodo(Frecuencia.DIARIO, 1);
            int puntos = 0;
            int xp = 0;
            for (int minutos : reparto) {
                Premio premio = dia.paso(LUNES, 30, minutos);
                puntos += premio.puntosTotales();
                xp += premio.xpTotal();
            }
            assertEquals(25, puntos, java.util.Arrays.toString(reparto));
            assertEquals(5, xp, java.util.Arrays.toString(reparto));
        }
    }

    @Test
    void semanal_cadaDiaVaPorSuLado_yElTercerDiaHechoTraeElExtra() {
        Periodo semana = new Periodo(Frecuencia.SEMANAL, 3);
        assertEquals(p(25, 5, 0, 0), semana.paso(LUNES, 60, 60));
        assertEquals(p(12, 2, 0, 0), semana.paso(LUNES.plusDays(1), 60, 30));
        assertEquals(p(25, 5, 0, 0), semana.paso(LUNES.plusDays(2), 60, 60));
        // Jueves: tercer día hecho. La base llega al tope de la semana
        // (3 × 25 = 75: ya iban 62) y el extra es el 20 % de 75 = 15.
        assertEquals(p(13, 3, 15, 3), semana.paso(LUNES.plusDays(3), 60, 60));
        // Terminar el martes ya no da nada: la semana está cumplida.
        assertEquals(p(0, 0, 0, 0), semana.paso(LUNES.plusDays(1), 60, 30));
    }

    @Test
    void semanal_variasSesionesElMismoDia_seSuman() {
        Periodo semana = new Periodo(Frecuencia.SEMANAL, 2);
        assertEquals(p(8, 1, 0, 0), semana.paso(LUNES, 60, 20));
        assertEquals(p(8, 2, 0, 0), semana.paso(LUNES, 60, 20));
        assertEquals(p(9, 2, 0, 0), semana.paso(LUNES, 60, 20));
        // Segundo día hecho con meta 2: extra del 20 % de 50.
        assertEquals(p(25, 5, 10, 2), semana.paso(LUNES.plusDays(1), 60, 75));
    }

    @Test
    void semanal_diasAMediasNoPasanDelTopeDeLaSemana() {
        // 59 de 60 los siete días: nunca hay un día hecho, y la semana no
        // puede cobrar más que tres días enteros.
        Periodo semana = new Periodo(Frecuencia.SEMANAL, 3);
        int puntos = 0;
        int xp = 0;
        int[] esperados = {24, 24, 24, 3, 0, 0, 0};
        for (int i = 0; i < 7; i++) {
            Premio premio = semana.paso(LUNES.plusDays(i), 60, 59);
            assertEquals(esperados[i], premio.puntos(), "día " + i);
            assertEquals(0, premio.puntosExtra());
            puntos += premio.puntosTotales();
            xp += premio.xpTotal();
        }
        assertEquals(75, puntos);
        assertEquals(15, xp);
    }

    @Test
    void semanal_metaUno_unDiaHechoCompletaLaSemana() {
        Periodo semana = new Periodo(Frecuencia.SEMANAL, 1);
        assertEquals(p(25, 5, 5, 1), semana.paso(LUNES, 45, 45));
        assertEquals(Premio.NADA, semana.paso(LUNES.plusDays(1), 45, 45));
    }
}
