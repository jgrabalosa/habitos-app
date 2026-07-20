package com.joaquim.habitosapp.model;

import java.time.DayOfWeek;
import java.time.LocalDate;

public enum Frecuencia {
    DIARIO,
    SEMANAL;

    /**
     * Rango [desde, hasta] del periodo actual de esta frecuencia.
     * DIARIO = hoy; SEMANAL = semana natural (lunes a domingo).
     * Única fuente de verdad: añadir MENSUAL = añadir su caso aquí.
     */
    public LocalDate[] rangoPeriodoActual() {
        LocalDate hoy = LocalDate.now();
        return switch (this) {
            case SEMANAL -> {
                LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
                yield new LocalDate[]{lunes, lunes.plusDays(6)};
            }
            case DIARIO -> new LocalDate[]{hoy, hoy};
        };
    }
}