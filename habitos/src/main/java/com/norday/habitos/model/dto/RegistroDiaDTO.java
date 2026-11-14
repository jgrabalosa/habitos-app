package com.norday.habitos.model.dto;

import java.time.LocalDate;

public class RegistroDiaDTO {

    private LocalDate fecha;
    private boolean completado;
    private int veces; // nº de completados ese día (para intensidad del heatmap)
    // Los minutos de ese día en un hábito de minutos (null en VECES). Con
    // ellos la app enseña los días a medias; completado dice si el día llegó
    // a sus minutos.
    private Integer minutos;

    public RegistroDiaDTO(LocalDate fecha, boolean completado, int veces) {
        this(fecha, completado, veces, null);
    }

    public RegistroDiaDTO(LocalDate fecha, boolean completado, int veces, Integer minutos) {
        this.fecha = fecha;
        this.completado = completado;
        this.veces = veces;
        this.minutos = minutos;
    }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public int getVeces() { return veces; }
    public void setVeces(int veces) { this.veces = veces; }

    public Integer getMinutos() { return minutos; }
    public void setMinutos(Integer minutos) { this.minutos = minutos; }
}
