package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;

import java.util.List;
import java.util.Map;

public interface IHabitoObjetivoDAO {

    void save(HabitoObjetivo version);

    /**
     * Guarda una versión nueva sin chocar con la restricción única
     * (habito_ref, desde): true si se ha escrito; false si otra petición
     * acaba de escribir una para ese hábito y esa fecha. La transacción
     * sigue viva en los dos casos.
     */
    boolean insertarSiLibre(HabitoObjetivo version);

    /** Borra las versiones del hábito con desde posterior a esa fecha. */
    void deletePosterioresA(Habito habito, java.time.LocalDate fecha);
    void update(HabitoObjetivo version);
    void delete(int id);

    /** Las versiones del hábito, de la más antigua a la más reciente. */
    List<HabitoObjetivo> findByHabito(Habito habito);

    /**
     * Las versiones de varios hábitos en una sola consulta, por id de
     * hábito y de la más antigua a la más reciente. Un hábito sin versiones
     * no está en el mapa.
     */
    Map<Integer, List<HabitoObjetivo>> findByHabitos(List<Habito> habitos);

    /** Borrado en bloque al eliminar el hábito o la cuenta. */
    void deleteByHabito(int habitoId);
}
