package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.service.TextosService;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * El historial de objetivos de los hábitos (ver {@link HabitoObjetivo}).
 *
 * {@link Habito} guarda una copia del objetivo vigente hoy, para las
 * lecturas rápidas y para la app ya instalada. Cuando llega la fecha de un
 * cambio programado, la copia se pone al día al leer, como la racha: no hay
 * proceso nocturno. Por eso las lecturas de Hoy, del detalle y de las
 * listas, y el completado, llaman antes a {@link #ponerAlDia}.
 *
 * Desde cuándo se aplica un cambio lo decide
 * {@link com.norday.habitos.model.CalculoObjetivo}; aquí solo se guarda.
 */
@Service
public class ObjetivoService {

    @Autowired
    private IHabitoObjetivoDAO objetivoDAO;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private RitmoService ritmoService;

    @Autowired
    private TextosService textosService;

    /** Al crear un hábito: su primera versión, desde la fecha de inicio. */
    @Transactional
    public void crearInicial(Habito habito) {
        objetivoDAO.save(new HabitoObjetivo(habito, habito.getFechaInicio(), Objetivo.de(habito)));
    }

    /**
     * Los historiales de varios hábitos, por id de hábito, en una sola
     * consulta. Para juzgar varios días sin una consulta por día o por
     * hábito. Un hábito sin versiones no está en el mapa: se lee con
     * {@link HistorialObjetivos#de(Map, Habito)}.
     */
    @Transactional
    public Map<Integer, HistorialObjetivos> historiales(List<Habito> habitos) {
        Map<Integer, HistorialObjetivos> historiales = new HashMap<>();
        Map<Integer, List<HabitoObjetivo>> versiones = objetivoDAO.findByHabitos(habitos);
        for (Habito habito : habitos) {
            List<HabitoObjetivo> suyas = versiones.get(habito.getHabitoId());
            if (suyas != null) {
                historiales.put(habito.getHabitoId(), HistorialObjetivos.de(habito, suyas));
            }
        }
        return historiales;
    }

    /**
     * Pone al día la copia del objetivo que guarda cada hábito: si la
     * versión vigente hoy es otra —ha llegado la fecha de un cambio
     * programado—, la copia pasa a ser esa. Devuelve los historiales que ha
     * leído, para que quien llama no los lea dos veces.
     *
     * El ritmo se ancla en la fecha del cambio con el hábito tal como
     * estaba, igual que al cambiar el objetivo para hoy: lo ya cerrado se
     * juzga con las reglas antiguas y el periodo del cambio solo puede subir.
     * Por eso esto va antes de cualquier lectura del ritmo.
     */
    @Transactional
    public Map<Integer, HistorialObjetivos> ponerAlDia(List<Habito> habitos, LocalDate hoy) {
        Map<Integer, HistorialObjetivos> historiales = historiales(habitos);
        for (Habito habito : habitos) {
            HabitoObjetivo vigente = HistorialObjetivos.de(historiales, habito).versionEl(hoy);
            if (vigente == null) continue;
            Objetivo nuevo = Objetivo.de(vigente);
            Objetivo actual = Objetivo.de(habito);
            if (nuevo.equals(actual)) continue;

            // Mismo criterio que HabitoService.actualizar: el ritmo depende
            // de la frecuencia, la meta, la unidad y los minutos.
            if (actual.cambiaElCreditoRespectoA(nuevo)) {
                ritmoService.anclarAntesDeCambiar(habito, vigente.getDesde());
            }
            habito.setUnidad(nuevo.unidad());
            habito.setMinutosObjetivo(nuevo.minutosObjetivo());
            habito.setFrecuencia(nuevo.frecuencia());
            habito.setMeta(nuevo.meta());
            habito.setDiasSemana(nuevo.diasSemana());
            habitoDAO.update(habito);
        }
        return historiales;
    }

    /**
     * Anula el cambio programado: borra las versiones que empiezan después
     * de hoy. La copia del objetivo que guarda el hábito no se toca: es la de
     * hoy, y sigue siéndolo. El ritmo tampoco: un cambio programado no lo
     * ancla hasta que se aplica.
     */
    @Transactional
    public void anularProgramados(Habito habito, LocalDate hoy) {
        objetivoDAO.deletePosterioresA(habito, hoy);
    }

    /**
     * Guarda un cambio de objetivo que se aplica desde {@code desde}: hoy,
     * una fecha futura, o el lunes de esta semana en un cambio semanal sin
     * registros. Si ya había un cambio programado, este lo reemplaza: nunca
     * hay más de una versión futura. Con {@code desde} anterior a hoy, las
     * versiones de entre medias también desaparecen: solo cubrían días de
     * esta semana sin registros, y la semana pasa entera a la nueva.
     *
     * Un hábito sin versiones —anterior al historial, o creado sin pasar por
     * HabitoService— gana primero la suya, desde la fecha de inicio y con el
     * objetivo que tenía: sin ella, sus días anteriores al cambio se
     * juzgarían con el objetivo nuevo.
     */
    @Transactional
    public void programar(Habito habito, Objetivo nuevo, LocalDate desde, LocalDate hoy) {
        List<HabitoObjetivo> versiones = objetivoDAO.findByHabito(habito);
        if (versiones.isEmpty() && habito.getFechaInicio() != null
                && habito.getFechaInicio().isBefore(desde)) {
            insertarOConflicto(new HabitoObjetivo(habito, habito.getFechaInicio(), Objetivo.de(habito)));
        }

        // La versión que ya cae en esa fecha se reescribe, en vez de borrarla
        // y crear otra: Hibernate inserta antes de borrar al hacer flush, y
        // la restricción única (habito_ref, desde) saltaría.
        LocalDate seBorraTras = desde.isBefore(hoy) ? desde : hoy;
        HabitoObjetivo enLaFecha = null;
        for (HabitoObjetivo version : versiones) {
            if (version.getDesde().equals(desde)) {
                enLaFecha = version;
            } else if (version.getDesde().isAfter(seBorraTras)) {
                objetivoDAO.delete(version.getId());
            }
        }
        if (enLaFecha != null) {
            enLaFecha.aplicar(nuevo);
            objetivoDAO.update(enLaFecha);
        } else {
            insertarOConflicto(new HabitoObjetivo(habito, desde, nuevo));
        }
    }

    /**
     * Guarda una versión nueva. Si otra petición acaba de guardar una para
     * el mismo hábito y la misma fecha (dos PUT a la vez), no se escribe nada
     * y es un conflicto (409): la restricción única no llega a saltar, así
     * que no hay 500.
     */
    private void insertarOConflicto(HabitoObjetivo version) {
        if (!objetivoDAO.insertarSiLibre(version)) {
            throw new ConflictoException(textosService.texto("habito.objetivo.a.la.vez",
                    version.getHabito().getPropietario()));
        }
    }
}
