package com.norday.habitos.model;

/**
 * Los límites de los hábitos de minutos, en un solo sitio: los minutos de un
 * registro y los minutos por día del objetivo van de 1 a 600 (diez horas).
 */
public final class LimitesMinutos {

    public static final int MINIMO = 1;
    public static final int MAXIMO = 600;

    private LimitesMinutos() {}

    public static boolean valido(Integer minutos) {
        return minutos != null && minutos >= MINIMO && minutos <= MAXIMO;
    }
}
