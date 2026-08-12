package com.norday.habitos.model.dto;

import com.norday.habitos.model.Habito;

/** Un hábito en un día concreto de la semana, con si se completó ese día. */
public class HabitoDiaDTO {

    private Habito habito;
    private boolean completado;

    public HabitoDiaDTO(Habito habito, boolean completado) {
        this.habito = habito;
        this.completado = completado;
    }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }
}
