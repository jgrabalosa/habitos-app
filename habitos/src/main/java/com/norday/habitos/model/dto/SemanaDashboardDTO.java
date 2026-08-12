package com.norday.habitos.model.dto;

import java.util.List;

/** La semana completa: 7 días (lunes a domingo) más los SEMANAL flexibles. */
public class SemanaDashboardDTO {

    private List<DiaSemanaDTO> dias;
    private List<HabitoFlexibleDTO> flexibles;

    public SemanaDashboardDTO(List<DiaSemanaDTO> dias, List<HabitoFlexibleDTO> flexibles) {
        this.dias = dias;
        this.flexibles = flexibles;
    }

    public List<DiaSemanaDTO> getDias() { return dias; }
    public void setDias(List<DiaSemanaDTO> dias) { this.dias = dias; }

    public List<HabitoFlexibleDTO> getFlexibles() { return flexibles; }
    public void setFlexibles(List<HabitoFlexibleDTO> flexibles) { this.flexibles = flexibles; }
}
