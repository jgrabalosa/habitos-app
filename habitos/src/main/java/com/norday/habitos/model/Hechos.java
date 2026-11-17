package com.norday.habitos.model;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Lo hecho de un hábito en unos días, juzgado día a día con el objetivo que
 * tenía cada uno. Sin efectos: no lee la base de datos ni el reloj.
 *
 * Los "hechos" de un día son la suma de {@code cantidad} de sus registros:
 * en un hábito de veces, las veces (cada registro vale 1); en uno de
 * minutos, los minutos. Con eso y el objetivo del día (ver
 * {@link Objetivo#diaHecho}, {@link Objetivo#unidadesDelDia}) salen las
 * cuentas de Hoy, de los días hechos y del ritmo.
 *
 * Las "unidades" de un día son lo que cuenta para su periodo, en la unidad
 * de la meta: veces en un hábito de veces, días hechos (0 o 1) en uno de
 * minutos. En un hábito de veces las unidades de una semana son el número
 * de registros, igual que antes de los minutos.
 */
public final class Hechos {

    private final TreeMap<LocalDate, Integer> porDia;
    private final Function<LocalDate, Objetivo> objetivoEl;

    private Hechos(Map<LocalDate, Integer> porDia, Function<LocalDate, Objetivo> objetivoEl) {
        this.porDia = new TreeMap<>(porDia);
        this.objetivoEl = objetivoEl;
    }

    /** Lo hecho según unos registros; solo cuentan los completados. */
    public static Hechos de(Collection<Registro> registros, Function<LocalDate, Objetivo> objetivoEl) {
        Map<LocalDate, Integer> porDia = new TreeMap<>();
        for (Registro registro : registros) {
            if (registro.isCompletado()) {
                porDia.merge(registro.getFecha(), registro.getCantidad(), Integer::sum);
            }
        }
        return new Hechos(porDia, objetivoEl);
    }

    /**
     * Lo hecho de cada hábito, por id de hábito, a partir de los registros
     * de todos ellos (de una sola consulta) y de sus historiales: cada día
     * se juzga con el objetivo que el hábito tenía ese día. Todo hábito de
     * la lista está en el mapa, aunque no tenga registros.
     */
    public static Map<Integer, Hechos> porHabito(Collection<Registro> registros, Collection<Habito> habitos,
                                                 Map<Integer, HistorialObjetivos> historiales) {
        Map<Integer, List<Registro>> porId = new java.util.HashMap<>();
        for (Registro registro : registros) {
            porId.computeIfAbsent(registro.getHabito().getHabitoId(), id -> new ArrayList<>()).add(registro);
        }
        Map<Integer, Hechos> hechos = new java.util.HashMap<>();
        for (Habito habito : habitos) {
            HistorialObjetivos historial = HistorialObjetivos.de(historiales, habito);
            hechos.put(habito.getHabitoId(),
                    de(porId.getOrDefault(habito.getHabitoId(), List.of()), historial::objetivoEl));
        }
        return hechos;
    }

    /** Lo hecho a partir de los hechos de cada día ya sumados. */
    public static Hechos dePorDia(Map<LocalDate, Integer> porDia, Function<LocalDate, Objetivo> objetivoEl) {
        return new Hechos(porDia, objetivoEl);
    }

    /**
     * Lo hecho a partir de una fecha por registro de veces (repetida si ese
     * día se hizo varias veces), con un solo objetivo para todos los días.
     * Es la forma de antes de los minutos.
     */
    public static Hechos deFechas(Collection<LocalDate> fechas, Objetivo objetivo) {
        Map<LocalDate, Integer> porDia = new TreeMap<>();
        for (LocalDate fecha : fechas) {
            porDia.merge(fecha, 1, Integer::sum);
        }
        return new Hechos(porDia, fecha -> objetivo);
    }

    /** La suma de {@code cantidad} de ese día: las veces o los minutos. */
    public int cantidadEl(LocalDate fecha) {
        return porDia.getOrDefault(fecha, 0);
    }

    /** ¿Hay algún registro ese día, aunque el día no esté hecho? */
    public boolean algoEl(LocalDate fecha) {
        return cantidadEl(fecha) > 0;
    }

    /** ¿Hay algún registro? */
    public boolean algo() {
        return porDia.values().stream().anyMatch(cantidad -> cantidad > 0);
    }

    /** ¿Llega ese día a su objetivo? */
    public boolean diaHecho(LocalDate fecha) {
        return objetivoEl.apply(fecha).diaHecho(cantidadEl(fecha));
    }

    /** Lo que cuenta ese día para su periodo (ver la clase). */
    public int unidadesEl(LocalDate fecha) {
        return objetivoEl.apply(fecha).unidadesDelDia(cantidadEl(fecha));
    }

    /** Las unidades de los días hasta esa fecha, incluida. */
    public int unidadesHasta(LocalDate fecha) {
        int unidades = 0;
        for (LocalDate dia : porDia.headMap(fecha, true).keySet()) {
            unidades += unidadesEl(dia);
        }
        return unidades;
    }

    /** Las unidades de todos los días. */
    public int unidades() {
        int unidades = 0;
        for (LocalDate dia : porDia.keySet()) {
            unidades += unidadesEl(dia);
        }
        return unidades;
    }

    /** Cuántos días llegan a su objetivo. */
    public int diasHechos() {
        int dias = 0;
        for (LocalDate dia : porDia.keySet()) {
            if (diaHecho(dia)) dias++;
        }
        return dias;
    }

    /**
     * El crédito de un día de un hábito diario, de 0 a 1: de veces, las veces
     * entre la meta; de minutos, los minutos entre el objetivo. Pasarse no
     * suma más de 1.
     */
    public double creditoEl(LocalDate fecha) {
        Objetivo objetivo = objetivoEl.apply(fecha);
        int cantidad = cantidadEl(fecha);
        if (objetivo.enMinutos()) return objetivo.unidadesParcialesDelDia(cantidad);
        return CalculoRitmo.credito(cantidad, objetivo.meta());
    }

    /**
     * Una fecha por unidad, de la más antigua a la más reciente: en un hábito
     * de veces, una por registro; en uno de minutos, una por día hecho.
     */
    public List<LocalDate> fechasEnUnidades() {
        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate dia : porDia.keySet()) {
            fechas.addAll(Collections.nCopies(unidadesEl(dia), dia));
        }
        return fechas;
    }

    /**
     * Las fechas en que el hábito cuenta como hecho ese día, una vez cada
     * una: en un hábito de veces, las que tienen algún registro; en uno de
     * minutos, los días que llegan a su objetivo.
     */
    public List<LocalDate> fechasHechas() {
        List<LocalDate> fechas = new ArrayList<>();
        for (LocalDate dia : porDia.keySet()) {
            if (unidadesEl(dia) > 0) fechas.add(dia);
        }
        return fechas;
    }

    /** Lo mismo con un paso más, de {@code cantidad}, ese día. No cambia este. */
    public Hechos con(LocalDate fecha, int cantidad) {
        Map<LocalDate, Integer> nuevo = new TreeMap<>(porDia);
        nuevo.merge(fecha, cantidad, Integer::sum);
        return new Hechos(nuevo, objetivoEl);
    }

    /** Los hechos de cada día, sin modificar. */
    public Map<LocalDate, Integer> porDia() {
        return Collections.unmodifiableMap(porDia);
    }

    /** El objetivo con el que se juzga esa fecha. */
    public Objetivo objetivoEl(LocalDate fecha) {
        return objetivoEl.apply(fecha);
    }
}
