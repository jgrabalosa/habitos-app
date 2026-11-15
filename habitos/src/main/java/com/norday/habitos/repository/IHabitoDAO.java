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

    /**
     * Escribe en la copia del objetivo del hábito (unidad, minutos,
     * frecuencia, meta y días) el objetivo dado, y solo eso: un UPDATE de
     * esas columnas, sin tocar el resto. Para poner al día el objetivo desde
     * una lectura, que no bloquea y puede traer una copia vieja del hábito
     * (otro nombre, otro estado de pausa). Después el hábito en memoria queda
     * releído de la base de datos.
     */
    void actualizarObjetivo(Habito habito, com.norday.habitos.model.Objetivo objetivo);
}