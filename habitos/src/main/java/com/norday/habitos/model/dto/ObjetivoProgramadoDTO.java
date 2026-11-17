package com.norday.habitos.model.dto;

import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Unidad;

import java.time.LocalDate;

/**
 * Un cambio de objetivo que todavía no se aplica: desde cuándo y qué pedirá
 * el hábito entonces. Sale en el detalle para que la app pueda decirlo
 * ("Desde el lunes: 4 días por semana").
 */
public record ObjetivoProgramadoDTO(LocalDate desde, Unidad unidad, Frecuencia frecuencia,
                                    int meta, Integer minutosObjetivo, String diasSemana) {

    public static ObjetivoProgramadoDTO desde(HabitoObjetivo version) {
        return new ObjetivoProgramadoDTO(version.getDesde(), version.getUnidad(),
                version.getFrecuencia(), version.getMeta(), version.getMinutosObjetivo(),
                version.getDiasSemana());
    }
}
