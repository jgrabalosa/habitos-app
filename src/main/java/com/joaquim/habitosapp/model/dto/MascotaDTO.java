package com.joaquim.habitosapp.model.dto;

import java.time.LocalDate;

public class MascotaDTO {

    private String nombre;
    private int xp;
    private int nivel;
    private int xpEnNivelActual;
    private int xpParaSiguienteNivel;
    private String fase;
    private String estado;
    private LocalDate fechaUltimaComida;

    public MascotaDTO(String nombre, int xp, int nivel, int xpEnNivelActual,
                      int xpParaSiguienteNivel, String fase, String estado,
                      LocalDate fechaUltimaComida) {
        this.nombre = nombre;
        this.xp = xp;
        this.nivel = nivel;
        this.xpEnNivelActual = xpEnNivelActual;
        this.xpParaSiguienteNivel = xpParaSiguienteNivel;
        this.fase = fase;
        this.estado = estado;
        this.fechaUltimaComida = fechaUltimaComida;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public int getXp() { return xp; }
    public void setXp(int xp) { this.xp = xp; }

    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }

    public int getXpEnNivelActual() { return xpEnNivelActual; }
    public void setXpEnNivelActual(int xpEnNivelActual) { this.xpEnNivelActual = xpEnNivelActual; }

    public int getXpParaSiguienteNivel() { return xpParaSiguienteNivel; }
    public void setXpParaSiguienteNivel(int xpParaSiguienteNivel) { this.xpParaSiguienteNivel = xpParaSiguienteNivel; }

    public String getFase() { return fase; }
    public void setFase(String fase) { this.fase = fase; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public LocalDate getFechaUltimaComida() { return fechaUltimaComida; }
    public void setFechaUltimaComida(LocalDate fechaUltimaComida) { this.fechaUltimaComida = fechaUltimaComida; }
}