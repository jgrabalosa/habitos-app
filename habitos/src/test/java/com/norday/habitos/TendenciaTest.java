package com.norday.habitos;

import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.ParametrosRitmo;
import com.norday.habitos.model.Tendencia;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class TendenciaTest {

    private static double diario(double ritmo, List<Double> creditos) {
        return CalculoRitmo.cerrarPeriodos(ritmo, creditos, Frecuencia.DIARIO);
    }

    /** Sesenta días seguidos cumpliendo: prácticamente en el máximo. */
    private static double veterano() {
        return diario(ParametrosRitmo.VALOR_INICIAL, Collections.nCopies(60, 1.0));
    }

    @Test
    void subeConUnPuntoDeMejora() {
        assertEquals(Tendencia.SUBE, CalculoRitmo.tendencia(0.61, 0.60));
        assertEquals(Tendencia.IGUAL, CalculoRitmo.tendencia(0.605, 0.60));
        assertEquals(Tendencia.IGUAL, CalculoRitmo.tendencia(0.60, 0.60));
    }

    @Test
    void bajaSoloConUnaCaidaClara() {
        assertEquals(Tendencia.IGUAL, CalculoRitmo.tendencia(0.56, 0.60));
        assertEquals(Tendencia.BAJA, CalculoRitmo.tendencia(0.55, 0.60));
    }

    @Test
    void unSoloFalloEnUnaSemanaCompleta_nuncaDiceBaja() {
        // El peor caso: en el máximo, donde un fallo resta más y se recupera menos.
        double antes = veterano();
        for (int diaDelFallo = 0; diaDelFallo < 7; diaDelFallo++) {
            List<Double> semana = new ArrayList<>(Collections.nCopies(7, 1.0));
            semana.set(diaDelFallo, 0.0);

            Tendencia tendencia = CalculoRitmo.tendencia(diario(antes, semana), antes);

            assertEquals(Tendencia.IGUAL, tendencia, "fallo el día " + diaDelFallo);
        }
    }

    @Test
    void dosFallosSeguidosAlFinalDeLaSemana_dicenBaja() {
        double antes = veterano();
        double despues = diario(antes, List.of(1.0, 1.0, 1.0, 1.0, 1.0, 0.0, 0.0));

        assertEquals(Tendencia.BAJA, CalculoRitmo.tendencia(despues, antes));
    }

    @Test
    void unHabitoNuevoQueSeCumple_sube() {
        double antes = ParametrosRitmo.VALOR_INICIAL;
        double despues = diario(antes, Collections.nCopies(7, 1.0));

        assertEquals(Tendencia.SUBE, CalculoRitmo.tendencia(despues, antes));
    }

    @Test
    void semanal_unaSemanaCasiCompletaSeMantiene_yUnaFlojaBaja() {
        double antes = 0.94;
        double cuatroDeCinco = CalculoRitmo.cerrarPeriodo(antes, CalculoRitmo.credito(4, 5), Frecuencia.SEMANAL);
        double dosDeCinco = CalculoRitmo.cerrarPeriodo(antes, CalculoRitmo.credito(2, 5), Frecuencia.SEMANAL);

        assertEquals(Tendencia.IGUAL, CalculoRitmo.tendencia(cuatroDeCinco, antes));
        assertEquals(Tendencia.BAJA, CalculoRitmo.tendencia(dosDeCinco, antes));
    }
}
