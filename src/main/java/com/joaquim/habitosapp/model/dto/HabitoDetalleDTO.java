package com.joaquim.habitosapp.model.dto;

import java.time.LocalDate;
import java.util.List;

public class HabitoDetalleDTO {

    private int habitoId;
    private String nombre;
    private int rachaActual;
    private int rachaMaxima;
    private int totalCompletados;
    private int meta;
    private String frecuencia;
    private int completadosMesActual;
    private Double porcentajeMesActual; // null si frecuencia no es DIARIO (por ahora)
    private String mesConsultado;
    private List<RegistroDiaDTO> heatmap;
    private List<RegistroResumenDTO> ultimosRegistros;

    public int getHabitoId() { return habitoId; }
    public void setHabitoId(int habitoId) { this.habitoId = habitoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getRachaActual() { return rachaActual; }
    public void setRachaActual(int rachaActual) { this.rachaActual = rachaActual; }

    public int getRachaMaxima() { return rachaMaxima; }
    public void setRachaMaxima(int rachaMaxima) { this.rachaMaxima = rachaMaxima; }

    public int getTotalCompletados() { return totalCompletados; }
    public void setTotalCompletados(int totalCompletados) { this.totalCompletados = totalCompletados; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

    public String getFrecuencia() { return frecuencia; }
    public void setFrecuencia(String frecuencia) { this.frecuencia = frecuencia; }

    public int getCompletadosMesActual() { return completadosMesActual; }
    public void setCompletadosMesActual(int completadosMesActual) { this.completadosMesActual = completadosMesActual; }

    public Double getPorcentajeMesActual() { return porcentajeMesActual; }
    public void setPorcentajeMesActual(Double porcentajeMesActual) { this.porcentajeMesActual = porcentajeMesActual; }

    public String getMesConsultado() { return mesConsultado; }
    public void setMesConsultado(String mesConsultado) { this.mesConsultado = mesConsultado; }

    public List<RegistroDiaDTO> getHeatmap() { return heatmap; }
    public void setHeatmap(List<RegistroDiaDTO> heatmap) { this.heatmap = heatmap; }

    public List<RegistroResumenDTO> getUltimosRegistros() { return ultimosRegistros; }
    public void setUltimosRegistros(List<RegistroResumenDTO> ultimosRegistros) { this.ultimosRegistros = ultimosRegistros; }
}