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
        return rangoPeriodo(LocalDate.now(zona));
    }

    /** Rango del periodo que contiene la fecha indicada. */
    public LocalDate[] rangoPeriodo(LocalDate fecha) {
        return switch (this) {
            case SEMANAL -> {
                LocalDate lunes = fecha.with(DayOfWeek.MONDAY);
                yield new LocalDate[]{lunes, lunes.plusDays(6)};
            }
            case DIARIO -> new LocalDate[]{fecha, fecha};
        };
    }

    /**
     * Inicio del periodo inmediatamente anterior al actual. Sirve para saber
     * si una racha sigue viva: cuenta si la meta se cumplió en el periodo
     * actual o en el anterior.
     */
    public LocalDate inicioPeriodoAnterior(ZoneId zona) {
        return inicioPeriodoAnterior(LocalDate.now(zona));
    }

    /** Inicio del periodo anterior al que contiene la fecha indicada. */
    public LocalDate inicioPeriodoAnterior(LocalDate fecha) {
        LocalDate inicioActual = rangoPeriodo(fecha)[0];
        return switch (this) {
            case SEMANAL -> inicioActual.minusWeeks(1);
            case DIARIO -> inicioActual.minusDays(1);
        };
    }
}
