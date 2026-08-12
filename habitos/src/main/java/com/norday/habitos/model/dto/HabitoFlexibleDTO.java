package com.norday.habitos.model.dto;

import com.norday.habitos.model.Habito;

/**
 * Progreso agregado de un SEMANAL flexible (sin diasSemana) en toda la
 * semana: no tiene un día fijo que lo represente en la cuadrícula, así que
 * se cuenta aparte.
 */
public class HabitoFlexibleDTO {

    private Habito habito;
    private int completadosSemana;
    private int meta;

    public HabitoFlexibleDTO(Habito habito, int completadosSemana, int meta) {
        this.habito = habito;
        this.completadosSemana = completadosSemana;
        this.meta = meta;
    }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public int getCompletadosSemana() { return completadosSemana; }
    public void setCompletadosSemana(int completadosSemana) { this.completadosSemana = completadosSemana; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }
}
