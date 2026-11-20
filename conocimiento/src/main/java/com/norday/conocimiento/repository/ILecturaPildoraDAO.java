package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.LecturaPildora;

import java.time.LocalDate;
import java.util.List;

public interface ILecturaPildoraDAO {

    void save(LecturaPildora lectura);

    /** Las lecturas del usuario ese día (en su zona), en el orden en que se hicieron. */
    List<LecturaPildora> findByUsuarioYDia(int usuarioId, LocalDate dia);

    /** Todo el historial, con su píldora cargada: para la exportación RGPD. */
    List<LecturaPildora> findByUsuario(int usuarioId);

    /** Borrado en bloque al eliminar la cuenta. Ver LimpiadorConocimiento. */
    void deleteByUsuario(int usuarioId);
}
