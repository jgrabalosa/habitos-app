package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HitoPagado;
import com.norday.habitos.model.Registro;

import java.util.List;

public interface IHitoPagadoDAO {

    /**
     * Apunta que el hábito ha cobrado el umbral, ligado al registro que lo
     * paga. Devuelve true si la fila se ha escrito y false si ya estaba: con
     * dos completados a la vez, solo uno la escribe, y solo ese paga.
     */
    boolean apuntar(Habito habito, int umbral, Registro registro);

    /** Los umbrales que el hábito ya ha cobrado. */
    List<Integer> findUmbralesByHabito(Habito habito);

    /** Las filas del libro del hábito, de menor a mayor umbral. */
    List<HitoPagado> findByHabito(Habito habito);

    /** Al deshacer: borra los hitos que pagó ese completado. */
    void deleteByRegistro(int registroId);

    /** Borrado en bloque al eliminar el hábito o la cuenta. */
    void deleteByHabito(int habitoId);
}
