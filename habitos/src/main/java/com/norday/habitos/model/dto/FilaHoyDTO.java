package com.norday.habitos.model.dto;

import java.util.List;

/**
 * Un hábito en una de las dos secciones de Hoy.
 *
 * {@code completados} y {@code meta} son el "3/4" de la fila: en un diario,
 * lo hecho el día que se mira; en un semanal, lo hecho en la semana.
 * {@code hecho} dice si cuenta en el contador de su sección.
 * {@code completadoEnFecha}, si se marcó el día que se mira.
 * {@code ritmoNivel} va de 1 a 5 y es siempre el de hoy.
 * {@code fechasCompletadas} son las fechas ISO de la semana en que se hizo.
 */
public record FilaHoyDTO(HabitoDTO habito, int completados, int meta, boolean hecho,
                         boolean completadoEnFecha, int ritmoNivel, List<String> fechasCompletadas) {}
