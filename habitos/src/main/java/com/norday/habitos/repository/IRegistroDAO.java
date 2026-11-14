package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import java.time.LocalDate;
import java.util.List;

public interface IRegistroDAO {

    void save(Registro registro);

    /**
     * Guarda un registro que trae clave del cliente, sin chocar si otro
     * completado con la misma clave se ha guardado a la vez: devuelve el
     * registro guardado (ya gestionado), o null si la clave ya existía y no se
     * ha escrito nada. La transacción sigue viva en los dos casos.
     */
    Registro guardarSiClaveLibre(Registro registro);

    /** El registro con esa clave del cliente; null si no hay ninguno. */
    Registro findByClave(java.util.UUID clave);
    Registro findById(int id);
    List<Registro> findByHabito(Habito habito);
    Registro findByHabitoAndFecha(Habito habito, LocalDate fecha);
    List<Registro> findByFecha(LocalDate fecha);
    boolean existeRegistroEnFecha(Habito habito, LocalDate fecha);
    void delete(int id);
    void deleteByHabito(int habitoId);
    List<Registro> findByHabitoAndRango(Habito habito, LocalDate desde, LocalDate hasta);
    List<Registro> findByHabitosAndRango(List<Habito> habitos, LocalDate desde, LocalDate hasta);
    void update(Registro registro);
    /**
     * Los registros del usuario para los logros de volumen: cada fila en los
     * días de un hábito de veces, y como mucho una por hábito y día en los de
     * minutos (un día con cinco sesiones cuenta una). Cada día se juzga con
     * la unidad de la versión del objetivo vigente en esa fecha.
     */
    int contarPorUsuario(int usuarioId);
    int contarConNotaPorUsuario(int usuarioId);
    java.util.Map<Integer, Long> contarCompletadosPorUsuario(int usuarioId);
}