package com.norday.habitos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;

public enum Frecuencia {
    DIARIO,
    SEMANAL;

    /**
     * Rango [desde, hasta] del periodo actual de esta frecuencia, en la zona
     * del usuario. DIARIO = hoy; SEMANAL = semana natural (lunes a domingo).
     * Única fuente de verdad: añadir MENSUAL = añadir su caso aquí.
     *
     * La zona es obligatoria a propósito: ya no hay una medianoche, hay
     * veinticuatro, y el servidor no sabe cuál es la de este usuario.
     */
    public LocalDate[] rangoPeriodoActual(ZoneId zona) {
        LocalDate hoy = LocalDate.now(zona);
        return switch (this) {
            case SEMANAL -> {
                LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
                yield new LocalDate[]{lunes, lunes.plusDays(6)};
            }
            case DIARIO -> new LocalDate[]{hoy, hoy};
        };
    }

    /**
     * Inicio del periodo inmediatamente anterior al actual. Sirve para saber
     * si una racha sigue viva: cuenta si la meta se cumplió en el periodo
     * actual o en el anterior.
     */
    public LocalDate inicioPeriodoAnterior(ZoneId zona) {
        LocalDate inicioActual = rangoPeriodoActual(zona)[0];
        return switch (this) {
            case SEMANAL -> inicioActual.minusWeeks(1);
            case DIARIO -> inicioActual.minusDays(1);
        };
    }
}
