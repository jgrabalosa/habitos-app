package com.norday.habitos.model;

/**
 * Lo que un hábito pide en un periodo, sin nada más: la frecuencia, la meta
 * (veces al día en un diario, días por semana en un semanal) y los días
 * fijos de un semanal. Dos objetivos iguales juzgan igual cualquier día.
 *
 * Unos días fijos en blanco valen lo mismo que ninguno: el semanal es
 * flexible en los dos casos.
 */
public record Objetivo(Frecuencia frecuencia, int meta, String diasSemana) {

    public Objetivo {
        if (diasSemana != null && diasSemana.isBlank()) diasSemana = null;
    }

    /** El objetivo que el hábito guarda como vigente hoy. */
    public static Objetivo de(Habito habito) {
        return new Objetivo(habito.getFrecuencia(), habito.getMeta(), habito.getDiasSemana());
    }

    /** El objetivo de una versión del historial. */
    public static Objetivo de(HabitoObjetivo version) {
        return new Objetivo(version.getFrecuencia(), version.getMeta(), version.getDiasSemana());
    }
}
