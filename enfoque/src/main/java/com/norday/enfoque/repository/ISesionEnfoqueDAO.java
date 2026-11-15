package com.norday.enfoque.repository;

import com.norday.enfoque.model.EstadoAporte;
import com.norday.enfoque.model.SesionEnfoque;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public interface ISesionEnfoqueDAO {

    /**
     * Bloquea hasta el final de la transacción el alta de sesiones del
     * usuario (su fila de usuario, SELECT … FOR NO KEY UPDATE), para que dos
     * sesiones del mismo usuario no pasen a la vez la comprobación de solape.
     */
    void bloquearAltasDe(int usuarioId);

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
     * Apunta el resultado del aporte si la sesión sigue PENDIENTE (o si
     * estaba REPETIDO y el resultado es ACEPTADO: el que pagó gana). Devuelve
     * si ha escrito.
     */
    boolean guardarAporte(int sesionId, EstadoAporte estado, String motivo, int minutosAportados);

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
