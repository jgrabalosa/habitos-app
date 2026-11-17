package com.norday.habitos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

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
     * Un día del periodo de un paso de minutos: lo hecho ese día (la suma de
     * {@code cantidad}, es decir, los minutos) y los minutos de su objetivo.
     */
    public record DiaDelPeriodo(int hechos, int objetivo) {}

    /**
     * El premio de un paso de un hábito de minutos de {@code cantidad}
     * minutos el día {@code fecha}, cuyo objetivo es
     * {@code objetivoDelDia} minutos. {@code periodoAntes} son los días del
     * periodo (el día en un diario, la semana en un semanal) con algo hecho,
     * tal como estaban antes del paso.
     *
     * Cada día vale 25 puntos y 5 XP repartidos en proporción a sus minutos
     * hasta el objetivo: el día lleva cobrado acumulado(total, min(hechos,
     * objetivo), objetivo) = total·k/n en enteros, y el paso cobra lo que
     * sube eso. Con pasos de un minuto da las mismas cifras que un diario de
     * veces con meta igual al objetivo, y la suma del día es siempre 25 / 5.
     * Pasarse no da nada.
     *
     * En un semanal, la semana no cobra más que {@code meta} días enteros:
     * unos días a medias no pueden sumar más que la semana cumplida (en uno
     * de veces, las sesiones por encima de la meta tampoco dan nada). El
     * paso que hace que los días hechos de la semana lleguen a la meta añade
     * el extra del 20 % de {@code meta} días, como en uno de veces.
     */
    public static Premio delPasoEnMinutos(Frecuencia frecuencia, int meta,
                                          Map<LocalDate, DiaDelPeriodo> periodoAntes,
                                          LocalDate fecha, int objetivoDelDia, int cantidad) {
        int metaDias = frecuencia == Frecuencia.DIARIO ? 1 : Math.max(1, meta);
        int hechosAntes = periodoAntes.containsKey(fecha) ? periodoAntes.get(fecha).hechos() : 0;
        Map<LocalDate, DiaDelPeriodo> despues = new HashMap<>(periodoAntes);
        despues.put(fecha, new DiaDelPeriodo(hechosAntes + Math.max(0, cantidad), objetivoDelDia));

        int puntos = cobrado(ParametrosPremio.PUNTOS_POR_DIA, metaDias, despues)
                - cobrado(ParametrosPremio.PUNTOS_POR_DIA, metaDias, periodoAntes);
        int xp = cobrado(ParametrosPremio.XP_POR_DIA, metaDias, despues)
                - cobrado(ParametrosPremio.XP_POR_DIA, metaDias, periodoAntes);

        boolean completaLaSemana = frecuencia == Frecuencia.SEMANAL
                && diasHechos(periodoAntes) < metaDias && diasHechos(despues) >= metaDias;
        return new Premio(puntos, xp,
                completaLaSemana ? extra(ParametrosPremio.PUNTOS_POR_DIA * metaDias) : 0,
                completaLaSemana ? extra(ParametrosPremio.XP_POR_DIA * metaDias) : 0);
    }

    /** Lo cobrado en un periodo de minutos con esos días: cada día en proporción, y como mucho {@code metaDias} días. */
    private static int cobrado(int porDia, int metaDias, Map<LocalDate, DiaDelPeriodo> dias) {
        int suma = 0;
        for (DiaDelPeriodo dia : dias.values()) {
            int objetivo = Math.max(1, dia.objetivo());
            suma += acumulado(porDia, Math.min(Math.max(0, dia.hechos()), objetivo), objetivo);
        }
        return Math.min(suma, porDia * metaDias);
    }

    private static int diasHechos(Map<LocalDate, DiaDelPeriodo> dias) {
        int hechos = 0;
        for (DiaDelPeriodo dia : dias.values()) {
            if (dia.hechos() >= Math.max(1, dia.objetivo())) hechos++;
        }
        return hechos;
    }

    /** Los días que llegan a su objetivo, cada uno juzgado con el suyo (ver {@link Hechos}). */
    public static int diasHechos(Hechos hechos) {
        return hechos.diasHechos();
    }

    /**
     * Los días hechos de un hábito, a partir de las fechas de sus registros
     * (una fecha por registro, repetida si ese día se hizo varias veces). Es
     * la misma unidad que en los puntos: en un diario, los días que llegaron
     * al objetivo; en un semanal, cada sesión es un día.
     */
    public static int diasHechos(Frecuencia frecuencia, int objetivo, Collection<LocalDate> fechas) {
        Objetivo siempre = new Objetivo(frecuencia, objetivo, null);
        return diasHechos(fechas, fecha -> siempre);
    }

    /**
     * Igual, juzgando cada día con el objetivo que tenía
     * ({@code objetivoEl}, normalmente {@link HistorialObjetivos#objetivoEl}):
     * subir la meta no hace que dejen de contar los días que llegaron a la
     * de entonces.
     */
    public static int diasHechos(Collection<LocalDate> fechas, Function<LocalDate, Objetivo> objetivoEl) {
        Map<LocalDate, Integer> vecesPorDia = new HashMap<>();
        for (LocalDate fecha : fechas) {
            vecesPorDia.merge(fecha, 1, Integer::sum);
        }
        return Hechos.dePorDia(vecesPorDia, objetivoEl).diasHechos();
    }

    /**
     * Los puntos de los hitos que se alcanzan al pasar de {@code diasAntes}
     * a {@code diasDespues} días hechos. Se compara por intervalo y no por
     * valor exacto: si el contador salta, se pagan todos los cruzados. Si no
     * avanza, no se paga nada. No mira el libro de hitos: el completado usa
     * {@link #hitosAPagar}, que descuenta los ya cobrados.
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
     * Los umbrales que hay que pagar al pasar de {@code diasAntes} a
     * {@code diasDespues} días hechos, sin los que el hábito ya cobró
     * ({@code yaPagados}, el libro de hitos). Se paga un umbral si se cruza
     * —{@code diasAntes < umbral <= diasDespues}— y no está en el libro. Los
     * días hechos ya no bajan al subir la meta (cada día se juzga con la de
     * entonces), pero el libro sigue siendo quien garantiza que un umbral
     * no se paga dos veces.
     * De menor a mayor.
     */
    public static List<Integer> hitosAPagar(int diasAntes, int diasDespues,
                                            Collection<Integer> yaPagados) {
        List<Integer> umbrales = new ArrayList<>();
        for (int[] hito : ParametrosPremio.HITOS) {
            int umbral = hito[0];
            if (umbral > diasAntes && umbral <= diasDespues && !yaPagados.contains(umbral)) {
                umbrales.add(umbral);
            }
        }
        return umbrales;
    }

    /** Los puntos que paga un umbral de ParametrosPremio.HITOS; 0 si no es un hito. */
    public static int puntosDelHito(int umbral) {
        for (int[] hito : ParametrosPremio.HITOS) {
            if (hito[0] == umbral) return hito[1];
        }
        return 0;
    }

    /**
     * ¿Es este paso una vuelta al hábito? Lo es el primer paso después de
     * un tiempo sin hacerlo. {@code fechasAnteriores} son las fechas de los
     * registros que el hábito ya tenía, sin contar el de ahora.
     *
     * En un diario, volver es retomarlo tras varios días seguidos sin
     * hacerlo. En un semanal se mide por semanas, para que un hábito de una
     * vez por semana no vuelva cada semana: tiene que haber pasado una
     * semana entera sin ninguna sesión.
     *
     * No es una vuelta empezar un hábito que nunca se había hecho, ni
     * rellenar un día pasado cuando ya hay algo hecho ese día o después.
     */
    public static boolean esUnaVuelta(Frecuencia frecuencia, LocalDate fecha,
                                      Collection<LocalDate> fechasAnteriores) {
        LocalDate ultima = null;
        for (LocalDate anterior : fechasAnteriores) {
            if (!anterior.isBefore(fecha)) return false;
            if (ultima == null || anterior.isAfter(ultima)) ultima = anterior;
        }
        if (ultima == null) return false;

        if (frecuencia == Frecuencia.SEMANAL) {
            LocalDate lunesDeLaSemanaAnterior = fecha.with(DayOfWeek.MONDAY).minusWeeks(1);
            return ultima.isBefore(lunesDeLaSemanaAnterior);
        }
        long diasSinHacerlo = ChronoUnit.DAYS.between(ultima, fecha) - 1;
        return diasSinHacerlo >= ParametrosPremio.DIAS_SIN_HACER_PARA_VOLVER;
    }

    /**
     * La parte de {@code total} que toca al paso {@code paso} de
     * {@code pasos}. Se reparte en enteros y lo que sobra cae en los
     * últimos pasos: 25 entre 4 son 6, 6, 6 y 7. La suma de todos los
     * pasos es siempre {@code total}.
     */
    static int parte(int total, int paso, int pasos) {
        return acumulado(total, paso, pasos) - acumulado(total, paso - 1, pasos);
    }

    /**
     * Lo que llevan cobrado de {@code total} los primeros {@code hechos} de
     * {@code objetivo}, en enteros: total·hechos/objetivo. {@link #parte} es
     * lo que sube al dar un paso de uno; un paso de minutos sube lo que
     * suban sus minutos.
     */
    static int acumulado(int total, int hechos, int objetivo) {
        return total * hechos / objetivo;
    }

    private static int extra(int ganadoEnLaSemana) {
        return ganadoEnLaSemana * ParametrosPremio.EXTRA_SEMANA_COMPLETA_POR_CIENTO / 100;
    }
}
