package com.norday.habitos.model.dto;

import com.norday.habitos.model.Tendencia;

import java.util.List;

/**
 * La pantalla de Hoy, entera, para la app nueva.
 *
 * {@code fecha} es el día que se mira y {@code hoy} el día de hoy del
 * usuario; coinciden salvo cuando se toca otro día de la tira.
 * {@code dias} son los siete días de la semana de {@code fecha}.
 * {@code avance} es el del día que se mira, de 0 a 1; null si no tocaba nada.
 * {@code ritmoNivel} es el nivel del ritmo general, de 1 a 5, y
 * {@code ritmoTendencia} hacia dónde va respecto a hace siete días; los dos
 * son null si no hay hábitos activos.
 * {@code logrosOtorgados} son los códigos de los logros de ritmo que se
 * acaban de ganar en esta lectura, para que la app los celebre; vacío casi
 * siempre.
 */
public record HoyDTO(String fecha, String hoy, List<DiaTiraDTO> dias, Double avance,
                     Integer ritmoNivel, Tendencia ritmoTendencia,
                     SeccionHoyDTO delDia, SeccionHoyDTO deLaSemana,
                     List<String> logrosOtorgados) {}
