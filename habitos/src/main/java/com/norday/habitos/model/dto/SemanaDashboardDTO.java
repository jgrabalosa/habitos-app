package com.norday.habitos.model.dto;

import java.util.List;

/** La semana completa: 7 días (lunes a domingo) más los SEMANAL flexibles. */
public class SemanaDashboardDTO {

    private List<DiaSemanaDTO> dias;
    private List<HabitoFlexibleDTO> flexibles;

    /**
     * Hoy en la zona del usuario, en ISO (YYYY-MM-DD).
     *
     * No depende de qué semana se haya pedido: el cliente pregunta por
     * cualquier semana y siempre recibe qué día es hoy para esta persona.
     * Existe para que el cliente deje de calcularlo con el reloj de su
     * dispositivo, que no tiene por qué estar en la zona guardada del
     * usuario.
     */
    private String hoy;

    public SemanaDashboardDTO(List<DiaSemanaDTO> dias, List<HabitoFlexibleDTO> flexibles,
                              String hoy) {
        this.dias = dias;
        this.flexibles = flexibles;
        this.hoy = hoy;
    }

    public List<DiaSemanaDTO> getDias() { return dias; }
    public void setDias(List<DiaSemanaDTO> dias) { this.dias = dias; }

    public List<HabitoFlexibleDTO> getFlexibles() { return flexibles; }
    public void setFlexibles(List<HabitoFlexibleDTO> flexibles) { this.flexibles = flexibles; }

    public String getHoy() { return hoy; }
    public void setHoy(String hoy) { this.hoy = hoy; }
}
