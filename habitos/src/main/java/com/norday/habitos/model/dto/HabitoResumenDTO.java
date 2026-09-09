package com.norday.habitos.model.dto;

import com.norday.habitos.model.Habito;

public class HabitoResumenDTO {

    private HabitoDTO habito;
    private int totalCompletados;

    public HabitoResumenDTO(Habito habito, int totalCompletados) {
        this.habito = HabitoDTO.desde(habito);
        this.totalCompletados = totalCompletados;
    }

    public HabitoDTO getHabito() { return habito; }

    public int getTotalCompletados() { return totalCompletados; }
    public void setTotalCompletados(int totalCompletados) { this.totalCompletados = totalCompletados; }
}