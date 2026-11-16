package com.norday.enfoque.repository;

import com.norday.enfoque.model.EstadoAporte;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.SesionEnfoque;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ISesionEnfoqueDAO {

    SesionEnfoque findByClave(UUID clave);

    SesionEnfoque findById(int id);

    /**
     * La sesión tal como está en la base de datos, aunque la sesión de
     * Hibernate de la petición (open-in-view) ya tuviera una copia: después
     * de un UPDATE nativo, esa copia está vieja.
     */
    SesionEnfoque releer(int id);

    /**
     * Guarda la sesión si su clave está libre (INSERT … ON CONFLICT (clave)
     * DO NOTHING). Devuelve el id nuevo, o null si la clave ya existía: así
     * un choque no deja la transacción marcada para deshacer.
     */
    Integer insertarSiClaveLibre(SesionEnfoque sesion);

    /** ¿Se solapa [inicio, fin) con alguna sesión del usuario? */
    boolean haySolape(int usuarioId, LocalDateTime inicio, LocalDateTime fin);

    /**
     * Apunta el resultado del aporte (estado, motivo, minutos, monedas, XP y
     * logros) si gana al que hay: PENDIENTE lo acepta todo; ACEPTADO gana a
     * REPETIDO y a RECHAZADO; REPETIDO gana a RECHAZADO. Así, si dos reintentos
     * simultáneos responden distinto, queda el que pagó. Devuelve si ha escrito.
     */
    boolean guardarAporte(int sesionId, EstadoAporte estado, String motivo, int minutosAportados,
                          int monedas, int xp, String logros);

    /**
     * Apunta el premio de la sesión (V27). Solo se llama una vez, en el alta,
     * con el usuario bloqueado: no hay precedencia que resolver.
     */
    void guardarPremio(int sesionId, int monedas, int xp, String logros, boolean tope);

    /** Lo que ya han pagado las sesiones del usuario ese día: {monedas, xp}. */
    int[] premiadoDelDia(int usuarioId, LocalDate dia);

    /** Los minutos de las sesiones que cuentan del usuario ese día. */
    int minutosDelDia(int usuarioId, LocalDate dia);

    /** Las sesiones COMPLETADA del usuario ese día. */
    int completadasDelDia(int usuarioId, LocalDate dia);

    /** Cuántos días distintos tiene el usuario con alguna sesión que cuenta. */
    int diasConSesion(int usuarioId);

    /** Los minutos de todas las sesiones que cuentan del usuario. */
    long minutosTotales(int usuarioId);

    /** Los estados de las últimas {@code cuantas} sesiones del usuario, de la más reciente a la más antigua. */
    List<EstadoSesion> ultimosEstados(int usuarioId, int cuantas);

    /** El último día anterior a {@code dia} con una sesión que cuenta; null si ninguno. */
    LocalDate ultimoDiaQueCuentaAntesDe(int usuarioId, LocalDate dia);

    /** ¿Tiene el usuario alguna sesión que cuenta con día en {@code desde} o después? */
    boolean hayQueCuentaDesde(int usuarioId, LocalDate desde);

    /** Las sesiones del usuario con día entre desde y hasta (incluidos). */
    List<SesionEnfoque> findByUsuarioEntre(int usuarioId, LocalDate desde, LocalDate hasta);

    /** Los días distintos con alguna sesión que cuenta, hasta {@code hasta}, del más reciente al más antiguo. */
    List<LocalDate> diasQueCuentanHasta(int usuarioId, LocalDate hasta);

    /** Una página del historial, de la más reciente a la más antigua. */
    List<SesionEnfoque> pagina(int usuarioId, int pagina, int tamano);

    long contar(int usuarioId);

    /** Todas, de la más antigua a la más reciente (exportación). */
    List<SesionEnfoque> findByUsuario(int usuarioId);

    void deleteByUsuario(int usuarioId);
}
