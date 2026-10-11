package com.norday.habitos.model;

import java.time.LocalDate;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Lo que cobra cada paso, sin efectos: no lee la base de datos ni el reloj.
 *
 * Un día de hábito bien hecho vale lo mismo sea diario o semanal. En un
 * diario ese día se reparte entre sus veces; en un semanal cada sesión es
 * un día entero, y la que completa la semana añade un extra. Pasarse del
 * objetivo no da nada.
 */
public final class CalculoPremio {

    private CalculoPremio() {}

    /**
     * Lo que da un paso. {@code puntos} y {@code xp} son la parte del paso;
     * {@code puntosExtra} y {@code xpExtra}, lo que añade completar la
     * semana, y van aparte para poder explicarlo.
     */
    public record Premio(int puntos, int xp, int puntosExtra, int xpExtra) {

        public static final Premio NADA = new Premio(0, 0, 0, 0);

        public int puntosTotales() {
            return puntos + puntosExtra;
        }

        public int xpTotal() {
            return xp + xpExtra;
        }
    }

    /**
     * El premio del paso que se da ahora. {@code hechosAntes} son las veces
     * ya hechas en el periodo (el día en un diario, la semana en un
     * semanal) sin contar esta. Un objetivo menor que 1 se trata como 1.
     */
    public static Premio delPaso(Frecuencia frecuencia, int hechosAntes, int objetivo) {
        int meta = Math.max(1, objetivo);
        int antes = Math.max(0, hechosAntes);
        if (antes >= meta) return Premio.NADA;

        return switch (frecuencia) {
            case DIARIO -> new Premio(
                    parte(ParametrosPremio.PUNTOS_POR_DIA, antes + 1, meta),
                    parte(ParametrosPremio.XP_POR_DIA, antes + 1, meta),
                    0, 0);
            case SEMANAL -> {
                boolean completaLaSemana = antes + 1 == meta;
                yield new Premio(
                        ParametrosPremio.PUNTOS_POR_DIA,
                        ParametrosPremio.XP_POR_DIA,
                        completaLaSemana ? extra(ParametrosPremio.PUNTOS_POR_DIA * meta) : 0,
                        completaLaSemana ? extra(ParametrosPremio.XP_POR_DIA * meta) : 0);
            }
        };
    }

    /**
     * Los días hechos de un hábito, a partir de las fechas de sus registros
     * (una fecha por registro, repetida si ese día se hizo varias veces). Es
     * la misma unidad que en los puntos: en un diario, los días que llegaron
     * al objetivo; en un semanal, cada sesión es un día.
     */
    public static int diasHechos(Frecuencia frecuencia, int objetivo, Collection<LocalDate> fechas) {
        Map<LocalDate, Integer> vecesPorDia = new HashMap<>();
        for (LocalDate fecha : fechas) {
            vecesPorDia.merge(fecha, 1, Integer::sum);
        }
        if (frecuencia == Frecuencia.SEMANAL) return vecesPorDia.size();

        int meta = Math.max(1, objetivo);
        int dias = 0;
        for (int veces : vecesPorDia.values()) {
            if (veces >= meta) dias++;
        }
        return dias;
    }

    /**
     * Los puntos de los hitos que se alcanzan al pasar de {@code diasAntes}
     * a {@code diasDespues} días hechos. Se compara por intervalo y no por
     * valor exacto: si el contador salta, se pagan todos los cruzados. Si no
     * avanza, no se paga nada.
     */
    public static int puntosDeHitos(int diasAntes, int diasDespues) {
        int puntos = 0;
        for (int[] hito : ParametrosPremio.HITOS) {
            if (hito[0] > diasAntes && hito[0] <= diasDespues) {
                puntos += hito[1];
            }
        }
        return puntos;
    }

    /**
     * La parte de {@code total} que toca al paso {@code paso} de
     * {@code pasos}. Se reparte en enteros y lo que sobra cae en los
     * últimos pasos: 25 entre 4 son 6, 6, 6 y 7. La suma de todos los
     * pasos es siempre {@code total}.
     */
    static int parte(int total, int paso, int pasos) {
        return total * paso / pasos - total * (paso - 1) / pasos;
    }

    private static int extra(int ganadoEnLaSemana) {
        return ganadoEnLaSemana * ParametrosPremio.EXTRA_SEMANA_COMPLETA_POR_CIENTO / 100;
    }
}
