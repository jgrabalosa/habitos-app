package com.norday.habitos.model.dto;

/**
 * Un día de la tira de la semana. {@code avance} va de 0 a 1 y es null si
 * ese día no tocaba nada o si todavía no ha llegado.
 */
public record DiaTiraDTO(String fecha, Double avance) {}
