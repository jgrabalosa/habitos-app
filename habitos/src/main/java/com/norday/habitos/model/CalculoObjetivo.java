package com.norday.habitos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Desde cuándo se aplica un cambio de objetivo, sin efectos: no lee la base
 * de datos ni el reloj.
 *
 * Regla general: un cambio nunca reinterpreta un periodo en el que ya hay
 * registros. Si el periodo del cambio no tiene registros, se aplica ya, a
 * todo ese periodo; si los tiene, al empezar el periodo siguiente.
 *
 * El periodo depende de qué cambia. Lo que se juzga por días —la meta de
 * un diario que sigue diario (las veces al día), los minutos por día y el
 * paso de veces a minutos— tiene el día por periodo: sin registros hoy, el
 * cambio va desde hoy y, con ellos, desde mañana. Lo que se juzga por
 * semanas —la frecuencia, los días por semana o los días fijos de un
 * semanal— tiene la semana: sin registros esta semana, el cambio va desde
 * el lunes de esta semana, para que la semana entera se juzgue con una sola
 * regla (también un completado retroactivo); con ellos, desde el lunes
 * siguiente. Si un cambio mezcla las dos cosas, manda la semana: va entero
 * a una sola fecha.
 *
 * De minutos a veces no se puede cambiar ({@link #permitido}).
 *
 * Nada empieza antes de la fecha de inicio del hábito: uno creado a mitad
 * de semana cambia desde ese día.
 */
public final class CalculoObjetivo {

    private CalculoObjetivo() {}

    /** El periodo que no se puede reinterpretar al cambiar de objetivo. */
    public enum PeriodoDelCambio { NINGUNO, DIA, SEMANA }

    /**
     * ¿Se puede pasar de {@code actual} a {@code nuevo}? Todo menos volver de
     * minutos a veces: los minutos ya registrados no tienen equivalente en
     * veces.
     */
    public static boolean permitido(Objetivo actual, Objetivo nuevo) {
        return !(actual.enMinutos() && !nuevo.enMinutos());
    }

    /** Qué periodo protege el cambio de {@code actual} a {@code nuevo}; NINGUNO si son iguales. */
    public static PeriodoDelCambio periodoDelCambio(Objetivo actual, Objetivo nuevo) {
        if (actual.equals(nuevo)) return PeriodoDelCambio.NINGUNO;
        if (actual.frecuencia() != nuevo.frecuencia()) return PeriodoDelCambio.SEMANA;
        if (actual.frecuencia() == Frecuencia.SEMANAL) {
            // En un semanal, la meta y los días fijos son de la semana; la
            // unidad y los minutos, del día.
            Objetivo a = actual.normalizado();
            Objetivo b = nuevo.normalizado();
            boolean parteSemanal = a.meta() != b.meta()
                    || !java.util.Objects.equals(a.diasSemana(), b.diasSemana());
            return parteSemanal ? PeriodoDelCambio.SEMANA : PeriodoDelCambio.DIA;
        }
        return PeriodoDelCambio.DIA;
    }

    /**
     * Desde qué día se aplica el cambio; null si no cambia nada. Puede ser
     * anterior a hoy: el lunes de esta semana en un cambio semanal sin
     * registros.
     *
     * @param fechasConRegistro las fechas de los registros del hábito en la
     *        semana en curso, del lunes a hoy (basta con esas: son las únicas
     *        que pueden caer en el periodo del cambio)
     * @param fechaInicio un hábito que aún no ha empezado no puede cambiar
     *        antes de empezar
     */
    public static LocalDate fechaDeEfecto(Objetivo actual, Objetivo nuevo, LocalDate hoy,
                                          LocalDate fechaInicio, Collection<LocalDate> fechasConRegistro) {
        LocalDate efecto = switch (periodoDelCambio(actual, nuevo)) {
            case NINGUNO -> null;
            case DIA -> fechasConRegistro.contains(hoy) ? hoy.plusDays(1) : hoy;
            case SEMANA -> {
                LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
                boolean hayRegistros = fechasConRegistro.stream()
                        .anyMatch(fecha -> !fecha.isBefore(lunes) && !fecha.isAfter(hoy));
                yield hayRegistros ? lunes.plusWeeks(1) : lunes;
            }
        };
        if (efecto != null && fechaInicio != null && fechaInicio.isAfter(efecto)) {
            efecto = fechaInicio;
        }
        return efecto;
    }
}
