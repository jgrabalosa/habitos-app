package com.norday.habitos.model.dto;

import com.norday.habitos.model.OrigenRegistro;

import java.time.LocalDate;

public class RegistroResumenDTO {

    private int registroId;
    private LocalDate fecha;
    private boolean completado;
    private String nota;
    private Integer valoracion; // 1-5, null si no se valoró
    private int cantidad;           // minutos en un hábito de minutos, 1 en uno de veces
    private OrigenRegistro origen;  // MANUAL o ENFOQUE

    public RegistroResumenDTO(int registroId, LocalDate fecha, boolean completado, String nota, Integer valoracion) {
        this(registroId, fecha, completado, nota, valoracion, 1, OrigenRegistro.MANUAL);
    }

    public RegistroResumenDTO(int registroId, LocalDate fecha, boolean completado, String nota, Integer valoracion,
                              int cantidad, OrigenRegistro origen) {
        this.registroId = registroId;
        this.fecha = fecha;
        this.completado = completado;
        this.nota = nota;
        this.valoracion = valoracion;
        this.cantidad = cantidad;
        this.origen = origen;
    }

    public int getRegistroId() { return registroId; }
    public void setRegistroId(int registroId) { this.registroId = registroId; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public Integer getValoracion() { return valoracion; }
    public void setValoracion(Integer valoracion) { this.valoracion = valoracion; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public OrigenRegistro getOrigen() { return origen; }
    public void setOrigen(OrigenRegistro origen) { this.origen = origen; }
}
