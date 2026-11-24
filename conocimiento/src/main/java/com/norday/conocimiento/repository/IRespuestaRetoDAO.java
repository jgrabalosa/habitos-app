package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.RespuestaReto;

import java.time.LocalDate;
import java.util.List;

public interface IRespuestaRetoDAO {

    void save(RespuestaReto respuesta);

    /** Las respuestas del usuario ese día (en su zona), en el orden en que se dieron. */
    List<RespuestaReto> findByUsuarioYDia(int usuarioId, LocalDate dia);

    /** Todo el historial, con su reto cargado: para la exportación RGPD. */
    List<RespuestaReto> findByUsuario(int usuarioId);

    /** Borrado en bloque al eliminar la cuenta. Ver LimpiadorConocimiento. */
    void deleteByUsuario(int usuarioId);
}
