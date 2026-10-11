package com.norday.gamificacion.model.dto;

import java.time.LocalDate;

public class MascotaDTO {

    private String nombre;
    private int xp;
    private int nivel;
    private int xpEnNivelActual;
    private int xpParaSiguienteNivel;
    private String fase;

    /**
     * La fase que le toca por nivel, aunque el usuario esté viendo otra. El
     * cliente la necesita para saber qué fases ofrecer en el selector sin
     * cablear los umbrales de nivel, que viven sólo en MascotaService.
     */
    private String faseReal;
    private String estado;

    /**
     * El ánimo en cuatro estados: triste, dormida, feliz o exultante. Va en
     * un campo aparte de "estado" a propósito: "estado" lo leen las apps ya
     * instaladas, que no conocen "exultante", y sigue calculándose igual.
     */
    private String animo;
    private LocalDate fechaUltimaComida;

    public MascotaDTO(String nombre, int xp, int nivel, int xpEnNivelActual,
                      int xpParaSiguienteNivel, String fase, String faseReal, String estado,
                      String animo, LocalDate fechaUltimaComida) {
        this.nombre = nombre;
        this.xp = xp;
        this.nivel = nivel;
        this.xpEnNivelActual = xpEnNivelActual;
        this.xpParaSiguienteNivel = xpParaSiguienteNivel;
        this.fase = fase;
        this.faseReal = faseReal;
        this.estado = estado;
        this.animo = animo;
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

    public String getFaseReal() { return faseReal; }
    public void setFaseReal(String faseReal) { this.faseReal = faseReal; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getAnimo() { return animo; }
    public void setAnimo(String animo) { this.animo = animo; }

    public LocalDate getFechaUltimaComida() { return fechaUltimaComida; }
    public void setFechaUltimaComida(LocalDate fechaUltimaComida) { this.fechaUltimaComida = fechaUltimaComida; }
}