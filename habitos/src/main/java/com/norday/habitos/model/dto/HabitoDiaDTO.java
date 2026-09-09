package com.norday.habitos.model.dto;

import com.norday.habitos.model.Habito;

/** Un hábito en un día concreto de la semana, con si se completó ese día. */
public class HabitoDiaDTO {

    private HabitoDTO habito;
    private boolean completado;

    public HabitoDiaDTO(Habito habito, boolean completado) {
        this.habito = HabitoDTO.desde(habito);
        this.completado = completado;
    }

    public HabitoDTO getHabito() { return habito; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }
}
