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
     * Deja «sin categoría» (tipo = null) todos los hábitos que usan esa
     * categoría, activos o no, pausados o no: un solo UPDATE de la columna
     * {@code tipo_id}, sin tocar nada más. Para borrar una categoría sin que
     * la clave ajena lo impida. Devuelve cuántos hábitos cambió.
     */
    int quitarCategoria(int categoriaId);

    /**
     * Escribe en la copia del objetivo del hábito (unidad, minutos,
     * frecuencia, meta y días) el objetivo dado, y solo eso: un UPDATE de
     * esas columnas, sin tocar el resto, y solo si en la base de datos siguen
     * siendo las que tiene {@code habito} en memoria. Para poner al día el
     * objetivo desde una lectura, que no bloquea y puede traer una copia
     * vieja del hábito: ni pisa otras columnas (otro nombre, otro estado de
     * pausa) ni deshace un cambio de objetivo confirmado entretanto.
     * Devuelve si escribió. No toca el hábito en memoria (ver releer).
     */
    boolean actualizarObjetivo(Habito habito, com.norday.habitos.model.Objetivo nuevo);

    /**
     * Relee el hábito de la base de datos si está en la sesión; si no, le
     * pone en memoria el objetivo dado.
     */
    void releer(Habito habito, com.norday.habitos.model.Objetivo siNoEstaEnLaSesion);
}