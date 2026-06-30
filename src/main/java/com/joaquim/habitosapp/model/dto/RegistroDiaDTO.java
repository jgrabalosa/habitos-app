package com.joaquim.habitosapp.model.dto;

import java.time.LocalDate;

public class RegistroDiaDTO {

    private LocalDate fecha;
    private boolean completado;

    public RegistroDiaDTO(LocalDate fecha, boolean completado) {
        this.fecha = fecha;
        this.completado = completado;
    }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }
}