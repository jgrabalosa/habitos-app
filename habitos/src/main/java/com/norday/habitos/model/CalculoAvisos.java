package com.norday.habitos.model;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

/**
 * Las reglas de los avisos de la app (la vuelta y el momento débil), sin
 * efectos: no leen la base de datos ni el reloj. {@code AvisosAppScheduler} les
 * da los datos y manda lo que ellas deciden.
 *
 * Los avisos van ordenados del más reciente al más antiguo, como los devuelve
 * {@code IAvisoAppDAO}. {@code AvisoApp.enviadoEn} es UTC; para saber a qué día
 * del usuario pertenece un aviso se pasa por su zona.
 */
public final class CalculoAvisos {

    /** Hora local de la vuelta. */
    public static final int HORA_VUELTA = 19;

    /** Día y hora local del momento débil. */
    public static final DayOfWeek DIA_MOMENTO_DEBIL = DayOfWeek.FRIDAY;
    public static final int HORA_MOMENTO_DEBIL = 18;

    /** Días sin nada a partir de los cuales sale la segunda vuelta. La primera usa ParametrosPremio. */
    public static final int DIAS_SIN_HACER_PARA_SEGUNDA_VUELTA = 7;

    /** Como mucho, tantos avisos de la app por semana ISO local (y uno por día). */
    public static final int AVISOS_POR_SEMANA = 2;

    /** Cuántos avisos seguidos sin efecto hacen que la app se retire. */
    public static final int AVISOS_SIN_EFECTO_PARA_RETIRARSE = 3;

    /** Un aviso «sirve» si en estas horas siguientes hay un registro completado. */
    public static final Duration VENTANA_DE_EFECTO = Duration.ofHours(48);

    /** Cuánto se calla la app tras retirarse, contados desde que se cierra la ventana del último aviso. */
    public static final Duration RETIRADA = Duration.ofDays(30);

    /** Lo que falta de la meta para que aún sea «a tiro» el viernes por la tarde: viernes, sábado y domingo. */
    public static final int DIAS_QUE_QUEDAN_EL_VIERNES = 3;

    /** Cuántos textos hay de cada tipo (el momento débil, por cada forma: con uno o con varios). */
    public static final Map<TipoAviso, Integer> VARIANTES = Map.of(
            TipoAviso.VUELTA_1, 3,
            TipoAviso.VUELTA_2, 2,
            TipoAviso.MOMENTO_DEBIL, 2);

    private CalculoAvisos() {}

    /** ¿Es esa hora local la de la vuelta? */
    public static boolean esHoraDeVuelta(java.time.ZonedDateTime local) {
        return local.getHour() == HORA_VUELTA;
    }

    /** ¿Es ese instante local el del momento débil (viernes, 18:xx)? */
    public static boolean esHoraDeMomentoDebil(java.time.ZonedDateTime local) {
        return local.getDayOfWeek() == DIA_MOMENTO_DEBIL && local.getHour() == HORA_MOMENTO_DEBIL;
    }

    /** El día local del usuario al que pertenece un aviso. */
    public static LocalDate diaDe(AvisoApp aviso, ZoneId zona) {
        return aviso.getEnviadoEn().toInstant(ZoneOffset.UTC).atZone(zona).toLocalDate();
    }

    /**
     * ¿Ha llegado a su tope? Ya hay uno hoy, o ya van dos esta semana ISO
     * (lunes a domingo del usuario).
     */
    public static boolean topeAlcanzado(List<AvisoApp> avisos, LocalDate hoy, ZoneId zona) {
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        LocalDate domingo = hoy.with(DayOfWeek.SUNDAY);
        int estaSemana = 0;
        for (AvisoApp aviso : avisos) {
            LocalDate dia = diaDe(aviso, zona);
            if (dia.equals(hoy)) return true;
            if (!dia.isBefore(lunes) && !dia.isAfter(domingo)) estaSemana++;
        }
        return estaSemana >= AVISOS_POR_SEMANA;
    }

    /**
     * Qué aviso de vuelta toca hoy, o null si ninguno.
     *
     * {@code ultimaActividad} es el día del último registro completado en
     * cualquier hábito; null si nunca ha completado nada (no hay a qué volver).
     * Los días sin nada son los días enteros entre aquel y hoy, la misma cuenta
     * que decide si un paso es una vuelta ({@link CalculoPremio#esUnaVuelta})
     * y que pone triste a Nori.
     *
     * Cada vuelta sale una sola vez por ausencia: una ausencia es lo que va
     * desde ese último registro, y un aviso suyo es uno enviado después. Lo que
     * pasa a partir de {@code DIAS_SIN_HACER_PARA_SEGUNDA_VUELTA} es la segunda
     * y última; después, silencio hasta que complete algo.
     */
    public static TipoAviso vuelta(LocalDate hoy, LocalDate ultimaActividad, List<AvisoApp> avisos, ZoneId zona) {
        if (ultimaActividad == null || !ultimaActividad.isBefore(hoy)) return null;
        if (!CalculoPremio.esUnaVuelta(Frecuencia.DIARIO, hoy, List.of(ultimaActividad))) return null;

        long diasSinNada = ChronoUnit.DAYS.between(ultimaActividad, hoy) - 1;
        TipoAviso toca = diasSinNada >= DIAS_SIN_HACER_PARA_SEGUNDA_VUELTA
                ? TipoAviso.VUELTA_2
                : TipoAviso.VUELTA_1;
        for (AvisoApp aviso : avisos) {
            if (aviso.getTipo() == toca && diaDe(aviso, zona).isAfter(ultimaActividad)) return null;
        }
        return toca;
    }

    /**
     * ¿Se ha retirado la app? Los tres últimos avisos no sirvieron: ninguno
     * tiene un registro completado en las 48 h siguientes. Entonces calla
     * hasta 30 días después de que se cierre la ventana del último.
     *
     * Un aviso al que aún le queda ventana y no ha tenido efecto cuenta como
     * sin efecto: si no, tras una retirada saldría otro aviso en cuanto
     * acabara el silencio y otro más antes de saber si el primero sirvió. Si el
     * usuario completa algo dentro de la ventana, el siguiente barrido lo ve y
     * la app vuelve.
     *
     * @param completadosCreadosEn cuándo (UTC) marcó el usuario sus registros
     *        completados, al menos desde el tercer aviso más reciente
     */
    public static boolean retirada(List<AvisoApp> avisos, Collection<LocalDateTime> completadosCreadosEn,
                                   Instant ahora) {
        if (avisos.size() < AVISOS_SIN_EFECTO_PARA_RETIRARSE) return false;
        for (AvisoApp aviso : avisos.subList(0, AVISOS_SIN_EFECTO_PARA_RETIRARSE)) {
            if (tuvoEfecto(aviso, completadosCreadosEn)) return false;
        }
        Instant cierreDelUltimo = enviado(avisos.get(0)).plus(VENTANA_DE_EFECTO);
        return ahora.isBefore(cierreDelUltimo.plus(RETIRADA));
    }

    /** ¿Hace falta mirar los registros para decidir la retirada? Solo con tres avisos y el último aún en silencio. */
    public static boolean puedeEstarRetirada(List<AvisoApp> avisos, Instant ahora) {
        if (avisos.size() < AVISOS_SIN_EFECTO_PARA_RETIRARSE) return false;
        return ahora.isBefore(enviado(avisos.get(0)).plus(VENTANA_DE_EFECTO).plus(RETIRADA));
    }

    /** Desde cuándo hay que mirar los registros para decidir la retirada: el envío del tercer aviso. */
    public static LocalDateTime desdeParaRetirada(List<AvisoApp> avisos) {
        return avisos.get(AVISOS_SIN_EFECTO_PARA_RETIRARSE - 1).getEnviadoEn();
    }

    private static boolean tuvoEfecto(AvisoApp aviso, Collection<LocalDateTime> completadosCreadosEn) {
        Instant desde = enviado(aviso);
        Instant hasta = desde.plus(VENTANA_DE_EFECTO);
        for (LocalDateTime creado : completadosCreadosEn) {
            Instant instante = creado.toInstant(ZoneOffset.UTC);
            if (!instante.isBefore(desde) && !instante.isAfter(hasta)) return true;
        }
        return false;
    }

    private static Instant enviado(AvisoApp aviso) {
        return aviso.getEnviadoEn().toInstant(ZoneOffset.UTC);
    }

    /**
     * Los semanales a tiro un viernes por la tarde: activos, sin recordatorio
     * (los que lo tienen ya reciben el suyo), creados antes del lunes de esta
     * semana, a los que les faltan unidades de su meta y lo que falta cabe en
     * los días que quedan.
     *
     * Cada uno se juzga con el objetivo vigente hoy ({@code historial}), y lo
     * hecho con el de cada día ({@code hechos}), en la unidad de la meta: veces
     * en un hábito de veces, días hechos en uno de minutos.
     *
     * @param habitos los hábitos activos del usuario
     * @param historiales por id de hábito (ver {@link HistorialObjetivos#de(Map, Habito)})
     * @param hechosPorHabito por id de hábito, con los registros de la semana
     */
    public static List<Habito> semanalesATiro(List<Habito> habitos, Map<Integer, HistorialObjetivos> historiales,
                                              Map<Integer, Hechos> hechosPorHabito, LocalDate hoy) {
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
        List<Habito> aTiro = new ArrayList<>();
        for (Habito habito : habitos) {
            Habito vigente = HistorialObjetivos.de(historiales, habito).vistaEl(hoy);
            if (vigente.getFrecuencia() != Frecuencia.SEMANAL) continue;
            if (tieneRecordatorio(habito)) continue;
            if (habito.getFechaInicio() != null && !habito.getFechaInicio().isBefore(lunes)) continue;

            Hechos hechos = hechosPorHabito.get(habito.getHabitoId());
            int hechas = hechos == null ? 0 : hechos.unidades();
            int faltan = Math.max(1, vigente.getMeta()) - hechas;
            if (faltan >= 1 && faltan <= DIAS_QUE_QUEDAN_EL_VIERNES) aTiro.add(habito);
        }
        return aTiro;
    }

    /** ¿Recibe el usuario ya el recordatorio de este hábito? Lo manda el barrido de recordatorios si está activo y tiene hora. */
    public static boolean tieneRecordatorio(Habito habito) {
        return habito.isRecordatorioActivo() && habito.getRecordatorioHora() != null;
    }

    /**
     * La variante que toca: nunca la misma que la del aviso anterior del mismo
     * tipo, y todas por turno. Sin anterior, una según el usuario para que no
     * todos empiecen por la primera. Variantes numeradas desde 1.
     *
     * @param anterior la variante del último aviso del mismo tipo; 0 si no hay
     */
    public static int variante(TipoAviso tipo, int anterior, int usuarioId) {
        int total = VARIANTES.get(tipo);
        if (anterior < 1 || anterior > total) {
            return 1 + Math.floorMod(usuarioId, total);
        }
        return anterior % total + 1;
    }
}
