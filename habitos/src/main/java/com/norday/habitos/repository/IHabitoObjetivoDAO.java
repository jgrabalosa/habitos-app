package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;

import java.util.List;
import java.util.Map;

public interface IHabitoObjetivoDAO {

    void save(HabitoObjetivo version);
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
