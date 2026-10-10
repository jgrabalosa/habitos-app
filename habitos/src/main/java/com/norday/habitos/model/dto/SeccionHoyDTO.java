package com.norday.habitos.model.dto;

import java.util.List;

/** Una sección de Hoy: cuántos de sus hábitos cuentan como hechos, y sus filas. */
public record SeccionHoyDTO(int hechos, int total, List<FilaHoyDTO> habitos) {}
