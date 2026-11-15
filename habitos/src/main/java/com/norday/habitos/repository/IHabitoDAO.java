package com.norday.habitos.repository;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Habito;
import java.util.List;

public interface IHabitoDAO {

    void save(Habito habito);
    Habito findById(int id);
    List<Habito> findByPropietario(Usuario propietario);
    List<Habito> findActivos(Usuario propietario);
    void update(Habito habito);
    void delete(int id);
    List<Habito> findTodosActivos();

    /**
     * Bloquea la fila del hábito hasta el final de la transacción (SELECT …
     * FOR UPDATE) y la devuelve releída de la base de datos, ya con el
     * bloqueo: lo que confirmó quien lo tenía antes. null si ya no existe.
     * Para serializar lo que cambia un hábito: completar, deshacer, editar,
     * pausar y reanudar.
     */
    Habito bloquear(int habitoId);
}