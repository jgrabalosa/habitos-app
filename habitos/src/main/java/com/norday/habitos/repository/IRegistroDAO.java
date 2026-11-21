package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

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
     * El día del último registro completado de cada usuario, en cualquiera de
     * sus hábitos, en una sola consulta. Un usuario que nunca ha completado
     * nada no está en el mapa.
     */
    Map<Integer, LocalDate> ultimaFechaCompletadaPorUsuario(Collection<Integer> usuarioIds);

    /**
     * Cuándo (UTC) se marcaron los registros completados del usuario desde ese
     * instante, incluido, del más antiguo al más reciente. Es el momento en que
     * lo marcó, no el día al que corresponde el registro.
     */
    List<LocalDateTime> creadosCompletadosDesde(int usuarioId, LocalDateTime desdeUtc);

    /**
     * Escribe solo la nota del registro (un UPDATE de esa columna) y relee el
     * registro si está en la sesión. Devuelve cuántas filas cambió: 0 si el
     * registro ya no existe (deshecho entretanto).
     */
    int actualizarNota(int registroId, String nota);

    /** Igual, con la valoración. */
    int actualizarValoracion(int registroId, Integer valoracion);
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