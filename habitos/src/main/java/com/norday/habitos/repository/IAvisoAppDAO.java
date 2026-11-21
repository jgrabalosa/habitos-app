package com.norday.habitos.repository;

import com.norday.habitos.model.AvisoApp;

import java.time.LocalDateTime;
import java.util.List;

public interface IAvisoAppDAO {

    /** Apunta un aviso enviado. */
    AvisoApp save(AvisoApp aviso);

    /** Todos los avisos del usuario, del más reciente al más antiguo. */
    List<AvisoApp> findByUsuario(int usuarioId);

    /** Los avisos del usuario enviados desde {@code desdeUtc} (incluido), del más reciente al más antiguo. */
    List<AvisoApp> findByUsuarioDesde(int usuarioId, LocalDateTime desdeUtc);

    /** Borrado en bloque al eliminar la cuenta. */
    void deleteByUsuario(int usuarioId);
}
