package com.norday.habitos.model.dto;

import java.util.List;

/**
 * Un día de la cuadrícula semanal: su fecha y los hábitos que le tocan ese
 * día. Los SEMANAL flexibles (sin diasSemana) nunca entran aquí — van
 * agregados en {@link SemanaDashboardDTO#getFlexibles()}.
 */
public class DiaSemanaDTO {

    private String fecha; // ISO yyyy-MM-dd
    private List<HabitoDiaDTO> habitos;

    public DiaSemanaDTO(String fecha, List<HabitoDiaDTO> habitos) {
        this.fecha = fecha;
        this.habitos = habitos;
    }

    public String getFecha() { return fecha; }
    public void setFecha(String fecha) { this.fecha = fecha; }

    public List<HabitoDiaDTO> getHabitos() { return habitos; }
    public void setHabitos(List<HabitoDiaDTO> habitos) { this.habitos = habitos; }
}
