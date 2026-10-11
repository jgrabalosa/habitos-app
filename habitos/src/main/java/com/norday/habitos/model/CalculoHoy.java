package com.norday.habitos.model;

import com.norday.gamificacion.model.AvanceDeHoy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Set;
import java.util.TreeSet;

/**
 * Las reglas de la pantalla de Hoy, sin efectos: no leen la base de datos ni
 * el reloj.
 *
 * Hoy tiene dos secciones. La del día lleva los diarios y los semanales con
 * día fijo el día que les toca. La de la semana lleva los semanales
 * flexibles y los de día fijo cuando no es su día. Para terminar el día solo
 * cuenta la sección del día.
 *
 * En todos los métodos, {@code fechasDeLaSemana} son las fechas en que se
 * hizo el hábito dentro de la semana (lunes a domingo) de la fecha que se
 * mira, una por vez hecha: se repite si un día se hizo varias veces.
 */
public final class CalculoHoy {

    private CalculoHoy() {}

    /**
     * Los días fijos de un semanal, en ISO (1 = lunes ... 7 = domingo).
     * Vacío si es flexible o si no es semanal. Un trozo que no sea un número
     * se ignora, igual que hace el resto del servicio al comparar.
     */
    public static Set<Integer> diasFijos(Habito habito) {
        Set<Integer> dias = new TreeSet<>();
        String texto = habito.getDiasSemana();
        if (habito.getFrecuencia() != Frecuencia.SEMANAL || texto == null || texto.isBlank()) {
            return dias;
        }
        for (String trozo : texto.split(",")) {
            try {
                dias.add(Integer.parseInt(trozo.trim()));
            } catch (NumberFormatException e) {
                // no es un día: se ignora
            }
        }
        return dias;
    }

    /**
     * ¿Va el hábito en la sección del día de esa fecha? Un diario, siempre;
     * un semanal con días fijos, solo sus días; un flexible, nunca. Y nada
     * toca antes de existir: un hábito creado el jueves no deja pendiente el
     * lunes.
     */
    public static boolean tocaEl(Habito habito, LocalDate fecha) {
        if (habito.getFechaInicio() != null && fecha.isBefore(habito.getFechaInicio())) return false;
        if (habito.getFrecuencia() == Frecuencia.DIARIO) return true;
        return diasFijos(habito).contains(fecha.getDayOfWeek().getValue());
    }

    /**
     * ¿Va el hábito en la sección de la semana de esa fecha? Los semanales
     * que ese día no están en la sección del día: los flexibles siempre, y
     * los de día fijo antes de su día (se puede adelantar) y después (se
     * puede recuperar).
     */
    public static boolean vaEnLaSemanaDe(Habito habito, LocalDate fecha) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) return false;
        LocalDate domingo = fecha.with(DayOfWeek.SUNDAY);
        if (habito.getFechaInicio() != null && habito.getFechaInicio().isAfter(domingo)) return false;
        return !tocaEl(habito, fecha);
    }

    /** Cuántas veces se hizo el hábito ese día. */
    public static int hechosEl(Collection<LocalDate> fechasDeLaSemana, LocalDate fecha) {
        int veces = 0;
        for (LocalDate hecha : fechasDeLaSemana) {
            if (hecha.equals(fecha)) veces++;
        }
        return veces;
    }

    /**
     * Para un semanal con días fijos, en uno de sus días: ¿está cubierto ese
     * día? Lo está si se hizo ese día, o si hasta ese día lleva tantas
     * sesiones como días le tocaban.
     *
     * Así, adelantar cubre: hecho el lunes y el martes, el miércoles está
     * cubierto. Recuperar no cubre el día siguiente: fallado el lunes y hecho
     * el martes, el miércoles lleva una de dos. Y lo que se haga después no
     * rellena un día pasado: se apunta el día en que se hizo.
     */
    public static boolean cubierto(Habito habito, Collection<LocalDate> fechasDeLaSemana, LocalDate fecha) {
        if (fechasDeLaSemana.contains(fecha)) return true;

        int tocaban = 0;
        for (LocalDate dia = fecha.with(DayOfWeek.MONDAY); !dia.isAfter(fecha); dia = dia.plusDays(1)) {
            if (tocaEl(habito, dia)) tocaban++;
        }
        int hechas = 0;
        for (LocalDate hecha : fechasDeLaSemana) {
            if (!hecha.isAfter(fecha)) hechas++;
        }
        return tocaban > 0 && hechas >= tocaban;
    }

    /** ¿Cuenta como hecho en la sección del día? Un diario, al llegar a su objetivo. */
    public static boolean hechoEnElDia(Habito habito, Collection<LocalDate> fechasDeLaSemana, LocalDate fecha) {
        if (habito.getFrecuencia() == Frecuencia.DIARIO) {
            return hechosEl(fechasDeLaSemana, fecha) >= Math.max(1, habito.getMeta());
        }
        return cubierto(habito, fechasDeLaSemana, fecha);
    }

    /** ¿Cuenta como hecho en la sección de la semana? Al llegar a su objetivo semanal. */
    public static boolean hechoEnLaSemana(Habito habito, Collection<LocalDate> fechasDeLaSemana) {
        return fechasDeLaSemana.size() >= Math.max(1, habito.getMeta());
    }

    /**
     * Lo que aporta un hábito de la sección del día al avance de ese día, de
     * 0 a 1. Un diario cuenta en proporción (3 de 4 aporta tres cuartos); un
     * semanal en su día fijo, todo o nada.
     */
    public static double creditoDelDia(Habito habito, Collection<LocalDate> fechasDeLaSemana, LocalDate fecha) {
        if (habito.getFrecuencia() == Frecuencia.DIARIO) {
            return CalculoRitmo.credito(hechosEl(fechasDeLaSemana, fecha), habito.getMeta());
        }
        return cubierto(habito, fechasDeLaSemana, fecha) ? 1.0 : 0.0;
    }

    /**
     * El avance de un día, de 0 a 1: la media de lo que aporta cada hábito
     * que tocaba. Es solo un dato —sin umbral ni premio— y solo mira la
     * sección del día. Vacío si ese día no tocaba nada.
     *
     * @param fechasPorHabito por id de hábito, sus fechas hechas de la semana
     */
    public static OptionalDouble avanceDelDia(List<Habito> habitos,
                                              Map<Integer, List<LocalDate>> fechasPorHabito,
                                              LocalDate fecha) {
        double suma = 0.0;
        int cuantos = 0;
        for (Habito habito : habitos) {
            if (!tocaEl(habito, fecha)) continue;
            suma += creditoDelDia(habito,
                    fechasPorHabito.getOrDefault(habito.getHabitoId(), List.of()), fecha);
            cuantos++;
        }
        return cuantos == 0 ? OptionalDouble.empty() : OptionalDouble.of(suma / cuantos);
    }

    /**
     * Cómo va hoy el usuario, para el ánimo de la mascota. Sigue lo que hace
     * hoy, no el ritmo.
     *
     * COMPLETO: todo lo de la sección del día está hecho. Un día sin sección
     * del día nunca lo es. EMPEZADO: ha dado algún paso hoy; vale uno parcial
     * y vale lo de la sección de la semana. SIN_EMPEZAR: hoy todavía nada.
     * AUSENTE: lleva tiempo sin hacer nada, con la misma cuenta que decide
     * si un paso es una vuelta ({@link CalculoPremio#esUnaVuelta}).
     *
     * Con solo hábitos semanales se mide por semanas, para que quien cumple
     * un semanal de una vez por semana no parezca ausente entre una vez y la
     * siguiente: EMPEZADO si esta semana ya ha hecho algo, y AUSENTE si
     * tampoco hizo nada la semana pasada.
     *
     * @param fechasPorHabito por id de hábito, sus fechas hechas de la semana de hoy
     * @param actividadReciente las fechas en que hizo algo últimamente, de
     *        cualquier hábito, más una fecha de referencia para quien nunca
     *        ha hecho nada (el día en que llegó)
     */
    public static AvanceDeHoy comoVa(List<Habito> habitos,
                                     Map<Integer, List<LocalDate>> fechasPorHabito,
                                     Collection<LocalDate> actividadReciente,
                                     LocalDate hoy) {
        boolean algoToca = false;
        boolean todoHecho = true;
        boolean algoHechoHoy = false;
        boolean algoHechoEstaSemana = false;
        boolean hayDiarios = false;
        for (Habito habito : habitos) {
            List<LocalDate> fechas = fechasPorHabito.getOrDefault(habito.getHabitoId(), List.of());
            if (habito.getFrecuencia() == Frecuencia.DIARIO) hayDiarios = true;
            if (!fechas.isEmpty()) algoHechoEstaSemana = true;
            if (fechas.contains(hoy)) algoHechoHoy = true;
            if (tocaEl(habito, hoy)) {
                algoToca = true;
                if (!hechoEnElDia(habito, fechas, hoy)) todoHecho = false;
            }
        }
        if (algoToca && todoHecho) return AvanceDeHoy.COMPLETO;

        boolean soloSemanales = !habitos.isEmpty() && !hayDiarios;
        if (soloSemanales ? algoHechoEstaSemana : algoHechoHoy) return AvanceDeHoy.EMPEZADO;

        Frecuencia medida = soloSemanales ? Frecuencia.SEMANAL : Frecuencia.DIARIO;
        return CalculoPremio.esUnaVuelta(medida, hoy, actividadReciente)
                ? AvanceDeHoy.AUSENTE
                : AvanceDeHoy.SIN_EMPEZAR;
    }
}
