package com.joaquim.habitosapp.model.dto;

import java.time.LocalDate;

public class RegistroResumenDTO {

    private int registroId;
    private LocalDate fecha;
    private boolean completado;
    private String nota;

    public RegistroResumenDTO(int registroId, LocalDate fecha, boolean completado, String nota) {
        this.registroId = registroId;
        this.fecha = fecha;
        this.completado = completado;
        this.nota = nota;
    }

    public int getRegistroId() { return registroId; }
    public void setRegistroId(int registroId) { this.registroId = registroId; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }
}