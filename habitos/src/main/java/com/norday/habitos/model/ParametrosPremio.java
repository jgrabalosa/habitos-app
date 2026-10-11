package com.norday.habitos.model;

/**
 * Los números de los premios, en un solo sitio: cuánto vale un día de
 * hábito bien hecho y cuánto añade completar la semana.
 */
public final class ParametrosPremio {

    /** Puntos de un día de hábito bien hecho, sea diario o semanal. */
    public static final int PUNTOS_POR_DIA = 25;

    /** Experiencia de un día de hábito bien hecho, sea diario o semanal. */
    public static final int XP_POR_DIA = 5;

    /**
     * Lo que añade completar la semana en un semanal, en tanto por ciento
     * de lo ganado esa semana con ese hábito. Vale para puntos y para
     * experiencia.
     */
    public static final int EXTRA_SEMANA_COMPLETA_POR_CIENTO = 20;

    /**
     * Los hitos de un hábito: días hechos y puntos que paga llegar a ellos.
     * Ordenados de menor a mayor. Se pagan una vez por hábito.
     */
    static final int[][] HITOS = {
            {3, 50}, {7, 100}, {30, 300}, {100, 750}, {365, 2000}
    };

    private ParametrosPremio() {}
}
