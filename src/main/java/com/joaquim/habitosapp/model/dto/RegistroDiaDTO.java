package com.joaquim.habitosapp.model.dto;

import java.time.LocalDate;

public class RegistroDiaDTO {

    private LocalDate fecha;
    private boolean completado;
    private int veces; // nº de completados ese día (para intensidad del heatmap)

    public RegistroDiaDTO(LocalDate fecha, boolean completado, int veces) {
        this.fecha = fecha;
        this.completado = completado;
        this.veces = veces;
    }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public int getVeces() { return veces; }
    public void setVeces(int veces) { this.veces = veces; }
}