package com.norday.habitos.model;

import java.util.List;

/**
 * El cálculo del ritmo, sin efectos: no lee la base de datos ni el reloj.
 *
 * El ritmo va de 0 a 1. Al cerrar cada periodo se acerca al crédito de ese
 * periodo —cuánto del objetivo se hizo—, más deprisa si sube que si baja.
 * Un periodo que sigue abierto no lo mueve: solo se cierran los que ya han
 * terminado.
 */
public final class CalculoRitmo {

    private CalculoRitmo() {}

    /**
     * Cuánto del objetivo se hizo en un periodo, de 0 a 1. Lo parcial cuenta
     * en proporción (3 de 4 vale 0,75) y pasarse del objetivo no suma más.
     * Un objetivo menor que 1 se trata como 1.
     */
    public static double credito(int hechos, int objetivo) {
        if (hechos <= 0) return 0.0;
        return Math.min(1.0, (double) hechos / Math.max(1, objetivo));
    }

    /**
     * El ritmo tras cerrar un periodo con ese crédito. Si el periodo fue
     * mejor que el ritmo, sube con la semivida corta; si fue peor, baja con
     * la larga.
     */
    public static double cerrarPeriodo(double ritmo, double credito, Frecuencia frecuencia) {
        double semivida = credito >= ritmo
                ? ParametrosRitmo.SEMIVIDA_AL_SUBIR_DIAS
                : ParametrosRitmo.SEMIVIDA_AL_BAJAR_DIAS;
        return ritmo + velocidad(semivida, frecuencia.diasDelPeriodo()) * (credito - ritmo);
    }

    /** El ritmo tras cerrar varios periodos seguidos, del más antiguo al más reciente. */
    public static double cerrarPeriodos(double ritmo, List<Double> creditos, Frecuencia frecuencia) {
        double actual = ritmo;
        for (double credito : creditos) {
            actual = cerrarPeriodo(actual, credito, frecuencia);
        }
        return actual;
    }

    /** Qué parte de la distancia al crédito se recorre en un periodo de esos días. */
    private static double velocidad(double semividaDias, int diasDelPeriodo) {
        return 1.0 - Math.pow(0.5, diasDelPeriodo / semividaDias);
    }
}
