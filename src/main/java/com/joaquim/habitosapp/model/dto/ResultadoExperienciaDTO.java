package com.joaquim.habitosapp.model.dto;

public class ResultadoExperienciaDTO {

    private boolean subioNivel;
    private int nivelNuevo;
    private MascotaDTO mascota;

    public ResultadoExperienciaDTO(boolean subioNivel, int nivelNuevo, MascotaDTO mascota) {
        this.subioNivel = subioNivel;
        this.nivelNuevo = nivelNuevo;
        this.mascota = mascota;
    }

    public boolean isSubioNivel() { return subioNivel; }
    public void setSubioNivel(boolean subioNivel) { this.subioNivel = subioNivel; }

    public int getNivelNuevo() { return nivelNuevo; }
    public void setNivelNuevo(int nivelNuevo) { this.nivelNuevo = nivelNuevo; }

    public MascotaDTO getMascota() { return mascota; }
    public void setMascota(MascotaDTO mascota) { this.mascota = mascota; }
}