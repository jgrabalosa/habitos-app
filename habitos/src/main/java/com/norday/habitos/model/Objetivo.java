package com.norday.habitos.model;

import java.util.Objects;
import java.util.TreeSet;

/**
 * Lo que un hábito pide en un periodo, sin nada más: la unidad, la
 * frecuencia, la meta (veces al día en un diario de veces, días por semana
 * en un semanal; en un diario de minutos, siempre 1), los minutos por día de
 * un hábito de minutos y los días fijos de un semanal.
 *
 * Unos días fijos en blanco valen lo mismo que ninguno: el semanal es
 * flexible en los dos casos. Una unidad null es VECES.
 *
 * Dos objetivos son iguales si juzgan igual cualquier día, aunque los datos
 * no estén escritos igual: {@link #equals} compara la forma normalizada
 * (ver {@link #normalizado}). Así editar solo el nombre de un hábito con
 * datos heredados incoherentes no crea una versión falsa del historial.
 */
public record Objetivo(Unidad unidad, Frecuencia frecuencia, int meta, Integer minutosObjetivo,
                       String diasSemana) {

    public Objetivo {
        if (unidad == null) unidad = Unidad.VECES;
        if (diasSemana != null && diasSemana.isBlank()) diasSemana = null;
    }

    /** Un objetivo de veces. */
    public Objetivo(Frecuencia frecuencia, int meta, String diasSemana) {
        this(Unidad.VECES, frecuencia, meta, null, diasSemana);
    }

    /** El objetivo que el hábito guarda como vigente hoy. */
    public static Objetivo de(Habito habito) {
        return new Objetivo(habito.getUnidad(), habito.getFrecuencia(), habito.getMeta(),
                habito.getMinutosObjetivo(), habito.getDiasSemana());
    }

    /** El objetivo de una versión del historial. */
    public static Objetivo de(HabitoObjetivo version) {
        return new Objetivo(version.getUnidad(), version.getFrecuencia(), version.getMeta(),
                version.getMinutosObjetivo(), version.getDiasSemana());
    }

    public boolean enMinutos() {
        return unidad == Unidad.MINUTOS;
    }

    /** Los minutos que tiene que sumar un día para estar hecho; como poco, 1. */
    public int minutosDelDia() {
        return minutosObjetivo == null ? 1 : Math.max(1, minutosObjetivo);
    }

    /**
     * ¿Está hecho un día con esos hechos (la suma de {@code cantidad} de sus
     * registros)? De veces: un diario, al llegar a su meta; un semanal, con
     * cualquier sesión. De minutos: al llegar a sus minutos.
     */
    public boolean diaHecho(int hechos) {
        if (enMinutos()) return hechos >= minutosDelDia();
        if (frecuencia == Frecuencia.SEMANAL) return hechos >= 1;
        return hechos >= Math.max(1, meta);
    }

    /**
     * Lo que cuenta un día para su periodo, en la unidad de la meta. De
     * veces, las veces. De minutos, 1 si el día está hecho y 0 si no: la meta
     * de un hábito de minutos cuenta días.
     */
    public int unidadesDelDia(int hechos) {
        if (enMinutos()) return diaHecho(hechos) ? 1 : 0;
        return Math.max(0, hechos);
    }

    /**
     * Igual, con el día a medias en proporción, para el crédito del ritmo y
     * del avance: de minutos, min(1, minutos / objetivo). De veces, las veces.
     */
    public double unidadesParcialesDelDia(int hechos) {
        if (hechos <= 0) return 0.0;
        if (enMinutos()) return Math.min(1.0, (double) hechos / minutosDelDia());
        return hechos;
    }

    /**
     * ¿Cambia de uno a otro lo que decide el crédito de un periodo (y por
     * tanto el ritmo)? La frecuencia, la meta, la unidad y los minutos; los
     * días fijos sin cambiar cuántos son, no.
     */
    public boolean cambiaElCreditoRespectoA(Objetivo otro) {
        Objetivo a = normalizado();
        Objetivo b = otro.normalizado();
        return a.frecuencia != b.frecuencia || a.meta != b.meta || a.unidad != b.unidad
                || !Objects.equals(a.minutosObjetivo, b.minutosObjetivo);
    }

    /**
     * La forma con la que se compara: los días fijos solo cuentan en un
     * semanal, y como conjunto ordenado ("4,2" es "2,4"); los minutos solo
     * cuentan en un hábito de minutos; y en un diario de minutos la meta es
     * siempre 1.
     */
    public Objetivo normalizado() {
        boolean semanal = frecuencia == Frecuencia.SEMANAL;
        boolean minutos = enMinutos();
        int metaNormal = minutos && frecuencia == Frecuencia.DIARIO ? 1 : meta;
        return new Objetivo(unidad, frecuencia, metaNormal, minutos ? minutosObjetivo : null,
                semanal ? diasOrdenados(diasSemana) : null);
    }

    private static String diasOrdenados(String dias) {
        if (dias == null) return null;
        TreeSet<String> ordenados = new TreeSet<>();
        for (String trozo : dias.split(",")) {
            if (!trozo.isBlank()) ordenados.add(trozo.trim());
        }
        return ordenados.isEmpty() ? null : String.join(",", ordenados);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Objetivo otro)) return false;
        Objetivo a = normalizado();
        Objetivo b = otro.normalizado();
        return a.unidad == b.unidad && a.frecuencia == b.frecuencia && a.meta == b.meta
                && Objects.equals(a.minutosObjetivo, b.minutosObjetivo)
                && Objects.equals(a.diasSemana, b.diasSemana);
    }

    @Override
    public int hashCode() {
        Objetivo n = normalizado();
        return Objects.hash(n.unidad, n.frecuencia, n.meta, n.minutosObjetivo, n.diasSemana);
    }
}
