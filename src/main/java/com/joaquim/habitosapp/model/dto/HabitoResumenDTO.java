package com.joaquim.habitosapp.model.dto;

import com.joaquim.habitosapp.model.Habito;

public class HabitoResumenDTO {

    private Habito habito;
    private int totalCompletados;

    public HabitoResumenDTO(Habito habito, int totalCompletados) {
        this.habito = habito;
        this.totalCompletados = totalCompletados;
    }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public int getTotalCompletados() { return totalCompletados; }
    public void setTotalCompletados(int totalCompletados) { this.totalCompletados = totalCompletados; }
}