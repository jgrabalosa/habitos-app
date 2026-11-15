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
    // Los minutos objetivo de la versión vigente ese día (null si ese día el
    // hábito era de VECES): con ellos la app dibuja el progreso de cada día.
    private Integer minutosObjetivo;

    public RegistroDiaDTO(LocalDate fecha, boolean completado, int veces) {
        this(fecha, completado, veces, null);
    }

    public RegistroDiaDTO(LocalDate fecha, boolean completado, int veces, Integer minutos) {
        this(fecha, completado, veces, minutos, null);
    }

    public RegistroDiaDTO(LocalDate fecha, boolean completado, int veces, Integer minutos,
                          Integer minutosObjetivo) {
        this.fecha = fecha;
        this.completado = completado;
        this.veces = veces;
        this.minutos = minutos;
        this.minutosObjetivo = minutosObjetivo;
    }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public int getVeces() { return veces; }
    public void setVeces(int veces) { this.veces = veces; }

    public Integer getMinutos() { return minutos; }
    public void setMinutos(Integer minutos) { this.minutos = minutos; }

    public Integer getMinutosObjetivo() { return minutosObjetivo; }
    public void setMinutosObjetivo(Integer minutosObjetivo) { this.minutosObjetivo = minutosObjetivo; }
}
