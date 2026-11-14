package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HitoPagado;

import java.util.List;

public interface IHitoPagadoDAO {

    void save(HitoPagado hito);

    /** Los umbrales que el hábito ya ha cobrado. */
    List<Integer> findUmbralesByHabito(Habito habito);

    /** Al deshacer: borra los hitos que pagó ese completado. */
    void deleteByRegistro(int registroId);

    /** Borrado en bloque al eliminar el hábito o la cuenta. */
    void deleteByHabito(int habitoId);
}
