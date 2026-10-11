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

    private ParametrosPremio() {}
}
