package com.norday.habitos.model;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * El cálculo del ritmo, sin efectos: no lee la base de datos ni el reloj.
 *
 * El ritmo va de 0 a 1. Al cerrar cada periodo se acerca al crédito de ese
 * periodo —cuánto del objetivo se hizo—, más deprisa si sube que si baja.
 * Un periodo que sigue abierto no lo mueve: solo se cierran los que ya han
 * terminado.
 */
public final class CalculoRitmo {

    private CalculoRitmo() {}

    /**
     * Un ancla puesta al día. {@code valor} es el ritmo vigente hoy.
     * {@code valorAncla} y {@code desdeAncla} son el ancla que se puede
     * guardar: el ritmo al empezar el día {@code desdeAncla}, con todo lo
     * anterior ya calculado.
     */
    public record RitmoAlDia(double valor, double valorAncla, LocalDate desdeAncla) {}

    /** El ritmo de un hábito y cuánto pesa en el ritmo general, de 0 a 1. */
    public record RitmoConPeso(double ritmo, double peso) {}

    /**
     * Cuánto del objetivo se hizo en un periodo, de 0 a 1. Lo parcial cuenta
     * en proporción (3 de 4 vale 0,75) y pasarse del objetivo no suma más.
     * Un objetivo menor que 1 se trata como 1.
     */
    public static double credito(int hechos, int objetivo) {
        if (hechos <= 0) return 0.0;
        return Math.min(1.0, (double) hechos / Math.max(1, objetivo));
    }

    /**
     * El ritmo tras cerrar un periodo con ese crédito. Si el periodo fue
     * mejor que el ritmo, sube con la semivida corta; si fue peor, baja con
     * la larga.
     */
    public static double cerrarPeriodo(double ritmo, double credito, Frecuencia frecuencia) {
        double semivida = credito >= ritmo
                ? ParametrosRitmo.SEMIVIDA_AL_SUBIR_DIAS
                : ParametrosRitmo.SEMIVIDA_AL_BAJAR_DIAS;
        return ritmo + velocidad(semivida, frecuencia.diasDelPeriodo()) * (credito - ritmo);
    }

    /** El ritmo tras cerrar varios periodos seguidos, del más antiguo al más reciente. */
    public static double cerrarPeriodos(double ritmo, List<Double> creditos, Frecuencia frecuencia) {
        double actual = ritmo;
        for (double credito : creditos) {
            actual = cerrarPeriodo(actual, credito, frecuencia);
        }
        return actual;
    }

    /**
     * Pone al día un ancla: cierra, en orden, todos los periodos que ya han
     * terminado desde {@code desde}. El periodo que contiene {@code hoy}
     * sigue abierto y no cuenta.
     *
     * Tres reglas:
     * - Un periodo semanal cuenta la semana entera, aunque el ancla caiga a
     *   mitad de semana: por eso se empieza en el inicio del periodo de
     *   {@code desde}, no en {@code desde}.
     * - El primer periodo de un hábito —el que contiene {@code fechaInicio}—
     *   solo puede subir el ritmo. Un hábito casi nunca nace al empezar un
     *   periodo, y no había tiempo de cumplirlo entero.
     * - El ancla avanza hasta el lunes de la semana de {@code hoy} y no más:
     *   solo se puede marcar o deshacer dentro de la semana en curso, así
     *   que lo anterior ya no cambia y lo de esta semana todavía puede.
     *
     * @param fechasHechas una fecha por cada vez que se hizo el hábito (se
     *                     repite si un día se hizo varias veces)
     */
    public static RitmoAlDia alDia(double valor, LocalDate desde, Frecuencia frecuencia, int objetivo,
                                   LocalDate fechaInicio, Collection<LocalDate> fechasHechas,
                                   LocalDate hoy) {
        Map<LocalDate, Integer> hechosPorPeriodo = new HashMap<>();
        for (LocalDate fecha : fechasHechas) {
            hechosPorPeriodo.merge(frecuencia.rangoPeriodo(fecha)[0], 1, Integer::sum);
        }
        LocalDate lunesDeEstaSemana = hoy.with(DayOfWeek.MONDAY);

        double actual = valor;
        double valorAncla = valor;
        LocalDate desdeAncla = desde;
        LocalDate cursor = frecuencia.rangoPeriodo(desde)[0];
        while (true) {
            LocalDate[] periodo = frecuencia.rangoPeriodo(cursor);
            if (!periodo[1].isBefore(hoy)) break; // sigue abierto: no mueve el ritmo

            double credito = credito(hechosPorPeriodo.getOrDefault(periodo[0], 0), objetivo);
            double siguiente = cerrarPeriodo(actual, credito, frecuencia);
            boolean esElPrimero = fechaInicio != null
                    && !fechaInicio.isBefore(periodo[0]) && !fechaInicio.isAfter(periodo[1]);
            if (esElPrimero && siguiente < actual) siguiente = actual;
            actual = siguiente;

            cursor = periodo[1].plusDays(1);
            if (!cursor.isAfter(lunesDeEstaSemana)) {
                valorAncla = actual;
                desdeAncla = cursor;
            }
        }
        return new RitmoAlDia(actual, valorAncla, desdeAncla);
    }

    /** El nivel de un ritmo, de 1 a 5. */
    public static int nivel(double ritmo) {
        if (ritmo >= ParametrosRitmo.NIVEL_5_DESDE) return 5;
        if (ritmo >= ParametrosRitmo.NIVEL_4_DESDE) return 4;
        if (ritmo >= ParametrosRitmo.NIVEL_3_DESDE) return 3;
        if (ritmo >= ParametrosRitmo.NIVEL_2_DESDE) return 2;
        return 1;
    }

    /**
     * Cuánto pesa un hábito en el ritmo general, de 0 a 1. El día que se crea
     * no pesa nada y va ganando peso cada día, hasta contar como los demás.
     * Sin fecha de inicio cuenta entero.
     */
    public static double pesoEnElGeneral(LocalDate fechaInicio, LocalDate hoy) {
        if (fechaInicio == null) return 1.0;
        long dias = ChronoUnit.DAYS.between(fechaInicio, hoy);
        if (dias <= 0) return 0.0;
        return Math.min(1.0, (double) dias / ParametrosRitmo.DIAS_DE_ENTRADA_EN_EL_GENERAL);
    }

    /**
     * El ritmo general: la media de los ritmos según su peso. Una sola
     * fuente —los ritmos de los hábitos—, para que ningún fallo se cuente
     * dos veces.
     *
     * Si ninguno pesa todavía (todos recién creados), es su media normal:
     * tiene que haber algo que enseñar desde el primer día. Sin hábitos no
     * hay ritmo general.
     */
    public static OptionalDouble general(Collection<RitmoConPeso> ritmos) {
        if (ritmos.isEmpty()) return OptionalDouble.empty();

        double sumaConPeso = 0.0;
        double pesos = 0.0;
        double sumaSimple = 0.0;
        for (RitmoConPeso ritmo : ritmos) {
            sumaConPeso += ritmo.ritmo() * ritmo.peso();
            pesos += ritmo.peso();
            sumaSimple += ritmo.ritmo();
        }
        if (pesos <= 0.0) return OptionalDouble.of(sumaSimple / ritmos.size());
        return OptionalDouble.of(sumaConPeso / pesos);
    }

    /** Qué parte de la distancia al crédito se recorre en un periodo de esos días. */
    private static double velocidad(double semividaDias, int diasDelPeriodo) {
        return 1.0 - Math.pow(0.5, diasDelPeriodo / semividaDias);
    }
}
