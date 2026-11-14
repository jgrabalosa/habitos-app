package com.norday.habitos.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/**
 * Las versiones del objetivo de un hábito, sin efectos: no lee la base de
 * datos ni el reloj.
 *
 * La versión vigente en una fecha es la de mayor {@code desde} que no la
 * pasa. Una fecha anterior a la primera versión —un registro anterior a la
 * fecha de inicio— se juzga con la primera. Un hábito sin versiones se
 * juzga siempre con el objetivo que guarda {@link Habito}: es lo que hacía
 * antes del historial, y lo que sigue haciendo si una lectura no lo trae.
 */
public final class HistorialObjetivos {

    private final Habito habito;
    private final List<HabitoObjetivo> versiones;

    private HistorialObjetivos(Habito habito, List<HabitoObjetivo> versiones) {
        this.habito = habito;
        List<HabitoObjetivo> ordenadas = new ArrayList<>(versiones);
        ordenadas.sort(Comparator.comparing(HabitoObjetivo::getDesde));
        this.versiones = List.copyOf(ordenadas);
    }

    public static HistorialObjetivos de(Habito habito, List<HabitoObjetivo> versiones) {
        return new HistorialObjetivos(habito, versiones);
    }

    /** El historial de un hábito sin versiones: siempre su objetivo de hoy. */
    public static HistorialObjetivos sinVersiones(Habito habito) {
        return new HistorialObjetivos(habito, List.of());
    }

    /** El historial del hábito en un mapa por id de hábito; sin versiones si no está. */
    public static HistorialObjetivos de(Map<Integer, HistorialObjetivos> historiales, Habito habito) {
        HistorialObjetivos historial = historiales.get(habito.getHabitoId());
        return historial != null ? historial : sinVersiones(habito);
    }

    /** Las versiones, de la más antigua a la más reciente. */
    public List<HabitoObjetivo> versiones() {
        return versiones;
    }

    /** La versión vigente en esa fecha; null si el hábito no tiene versiones. */
    public HabitoObjetivo versionEl(LocalDate fecha) {
        if (versiones.isEmpty()) return null;
        HabitoObjetivo vigente = versiones.get(0);
        for (HabitoObjetivo version : versiones) {
            if (version.getDesde().isAfter(fecha)) break;
            vigente = version;
        }
        return vigente;
    }

    /** El objetivo con el que se juzga esa fecha. */
    public Objetivo objetivoEl(LocalDate fecha) {
        HabitoObjetivo version = versionEl(fecha);
        return version != null ? Objetivo.de(version) : Objetivo.de(habito);
    }

    /** El hábito tal como era esa fecha: una vista con su objetivo (ver {@link Habito#conObjetivo}). */
    public Habito vistaEl(LocalDate fecha) {
        return habito.conObjetivo(objetivoEl(fecha));
    }

    /** El cambio programado: la primera versión con {@code desde} posterior a hoy; null si no hay. */
    public HabitoObjetivo programadaTras(LocalDate hoy) {
        for (HabitoObjetivo version : versiones) {
            if (version.getDesde().isAfter(hoy)) return version;
        }
        return null;
    }
}
