package com.norday.habitos.model.dto;

import com.norday.habitos.model.Unidad;

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
 *
 * En un hábito de minutos, {@code completados} y {@code meta} cuentan días,
 * para que la app ya instalada los entienda: en un diario, 0 o 1 (el día
 * hecho o no); en un semanal, los días hechos de la semana. {@code hecho} y
 * {@code completadoEnFecha} dicen si el día llegó a sus minutos, y
 * {@code fechasCompletadas} lleva una fecha por día hecho.
 * {@code minutosHechos} son los minutos del día que se mira y
 * {@code minutosObjetivo}, los de ese día; los dos null en VECES.
 */
public record FilaHoyDTO(HabitoDTO habito, int completados, int meta, boolean hecho,
                         boolean completadoEnFecha, int ritmoNivel, List<String> fechasCompletadas,
                         Unidad unidad, Integer minutosHechos, Integer minutosObjetivo) {}
