package com.norday.habitos;

import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.ParametrosRitmo;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las cifras esperadas NO salen de CalculoRitmo: salen de una simulación
 * aparte, con semivida de 7 días al subir, 21 al bajar e inicio en 0,5. Si se
 * cambian los parámetros en ParametrosRitmo, estos tests tienen que fallar y
 * hay que recalcular las cifras, no ajustarlas a lo que dé el código.
 */
class CalculoRitmoTest {

    private static final double D = 0.000001;
    private static final double INICIO = ParametrosRitmo.VALOR_INICIAL;

    private static List<Double> veces(int n, double credito) {
        return Collections.nCopies(n, credito);
    }

    private static double diario(double ritmo, int dias, double credito) {
        return CalculoRitmo.cerrarPeriodos(ritmo, veces(dias, credito), Frecuencia.DIARIO);
    }

    @Test
    void credito_esLaParteDelObjetivoHecha() {
        assertEquals(0.75, CalculoRitmo.credito(3, 4), D);
        assertEquals(0.6, CalculoRitmo.credito(3, 5), D);
        assertEquals(0.0, CalculoRitmo.credito(0, 5), D);
        assertEquals(1.0, CalculoRitmo.credito(5, 5), D);
    }

    @Test
    void credito_pasarseDelObjetivoNoSumaMas() {
        assertEquals(1.0, CalculoRitmo.credito(6, 5), D);
        assertEquals(1.0, CalculoRitmo.credito(40, 1), D);
    }

    @Test
    void credito_conObjetivoMenorQueUnoCuentaComoUno() {
        assertEquals(1.0, CalculoRitmo.credito(1, 0), D);
        assertEquals(0.0, CalculoRitmo.credito(0, 0), D);
    }

    @Test
    void diarioPerfectoDesdeElInicio_subeRapido() {
        assertEquals(0.547138, diario(INICIO, 1, 1.0), D);
        assertEquals(0.750000, diario(INICIO, 7, 1.0), D);
        assertEquals(0.875000, diario(INICIO, 14, 1.0), D);
        assertEquals(0.974365, diario(INICIO, 30, 1.0), D);
        assertEquals(0.998686, diario(INICIO, 60, 1.0), D);
    }

    @Test
    void unFalloTrasSesentaDiasPerfectos_cuestaPoco() {
        double veterano = diario(INICIO, 60, 1.0);

        assertEquals(0.966260, diario(veterano, 1, 0.0), D);
    }

    @Test
    void sieteDiasFuera_yLaVuelta() {
        double veterano = diario(INICIO, 60, 1.0);

        double trasLaSemanaFuera = diario(veterano, 7, 0.0);
        assertEquals(0.792657, trasLaSemanaFuera, D);
        assertEquals(0.896329, diario(trasLaSemanaFuera, 7, 1.0), D);
        assertEquals(0.948164, diario(trasLaSemanaFuera, 14, 1.0), D);
    }

    @Test
    void treintaDiasFuera_noLlegaACero() {
        double veterano = diario(INICIO, 60, 1.0);

        assertEquals(0.371010, diario(veterano, 30, 0.0), D);
    }

    @Test
    void semanal_dosSemanasCompletas() {
        double unaSemana = CalculoRitmo.cerrarPeriodo(INICIO, 1.0, Frecuencia.SEMANAL);
        double dosSemanas = CalculoRitmo.cerrarPeriodo(unaSemana, 1.0, Frecuencia.SEMANAL);

        assertEquals(0.750000, unaSemana, D);
        assertEquals(0.875000, dosSemanas, D);
    }

    @Test
    void semanal_cadaSesionDeLaTerceraSemanaCuenta() {
        double dosSemanas = CalculoRitmo.cerrarPeriodos(INICIO, veces(2, 1.0), Frecuencia.SEMANAL);
        double[] esperado = {0.694488, 0.735748, 0.777008, 0.818268, 0.859528, 0.937500};

        double anterior = -1.0;
        for (int hechos = 0; hechos <= 5; hechos++) {
            double ritmo = CalculoRitmo.cerrarPeriodo(
                    dosSemanas, CalculoRitmo.credito(hechos, 5), Frecuencia.SEMANAL);
            assertEquals(esperado[hechos], ritmo, D, "semana de " + hechos + "/5");
            assertTrue(ritmo > anterior, hechos + "/5 tiene que valer más que " + (hechos - 1) + "/5");
            anterior = ritmo;
        }
    }

    @Test
    void diarioConObjetivoCuatro_elDiaDeTresDeCuatroSube() {
        double dosDias = diario(INICIO, 2, 1.0);
        assertEquals(0.589832, dosDias, D);

        double[] esperado = {0.570682, 0.578799, 0.586916, 0.604932, 0.628501};
        for (int hechos = 0; hechos <= 4; hechos++) {
            double ritmo = CalculoRitmo.cerrarPeriodo(
                    dosDias, CalculoRitmo.credito(hechos, 4), Frecuencia.DIARIO);
            assertEquals(esperado[hechos], ritmo, D, "día de " + hechos + "/4");
        }
        assertTrue(esperado[3] > dosDias, "un día de 3/4 tiene que subir el ritmo");
    }

    @Test
    void subeMasDeprisaDeLoQueBaja() {
        double subida = diario(INICIO, 1, 1.0) - INICIO;
        double bajada = INICIO - diario(INICIO, 1, 0.0);

        assertEquals(0.047138, subida, D);
        assertEquals(0.016234, bajada, D);
        assertTrue(subida > 2.5 * bajada);
    }

    @Test
    void unaSemanaDeDiarioPerfectoEquivaleAUnaSemanaDeSemanalPerfecto() {
        double sieteDias = diario(INICIO, 7, 1.0);
        double unaSemana = CalculoRitmo.cerrarPeriodo(INICIO, 1.0, Frecuencia.SEMANAL);

        assertEquals(unaSemana, sieteDias, 0.000000001);
    }

    @Test
    void nuncaSaleDeCeroAUno() {
        double arriba = diario(INICIO, 2000, 1.0);
        double abajo = diario(INICIO, 2000, 0.0);

        assertTrue(arriba <= 1.0 && arriba > 0.999);
        assertTrue(abajo >= 0.0 && abajo < 0.001);
    }

    @Test
    void sinPeriodosCerrados_elRitmoNoCambia() {
        assertEquals(0.63, CalculoRitmo.cerrarPeriodos(0.63, List.of(), Frecuencia.DIARIO), 0.0);
        assertEquals(0.63, CalculoRitmo.cerrarPeriodos(0.63, List.of(), Frecuencia.SEMANAL), 0.0);
    }

    @Test
    void frecuencia_diasDelPeriodo() {
        assertEquals(1, Frecuencia.DIARIO.diasDelPeriodo());
        assertEquals(7, Frecuencia.SEMANAL.diasDelPeriodo());
    }
}
