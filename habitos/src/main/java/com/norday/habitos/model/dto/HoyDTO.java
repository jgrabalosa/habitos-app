package com.norday.habitos.model.dto;

import java.util.List;

/**
 * La pantalla de Hoy, entera, para la app nueva.
 *
 * {@code fecha} es el día que se mira y {@code hoy} el día de hoy del
 * usuario; coinciden salvo cuando se toca otro día de la tira.
 * {@code dias} son los siete días de la semana de {@code fecha}.
 * {@code avance} es el del día que se mira, de 0 a 1; null si no tocaba nada.
 * {@code ritmoNivel} es el nivel del ritmo general, de 1 a 5; null si no hay
 * hábitos activos.
 */
public record HoyDTO(String fecha, String hoy, List<DiaTiraDTO> dias, Double avance,
                     Integer ritmoNivel, SeccionHoyDTO delDia, SeccionHoyDTO deLaSemana) {}
