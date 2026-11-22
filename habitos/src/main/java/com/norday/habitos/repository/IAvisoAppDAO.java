package com.norday.habitos.repository;

import com.norday.habitos.model.AvisoApp;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IAvisoAppDAO {

    /** Apunta un aviso enviado. */
    AvisoApp save(AvisoApp aviso);

    /** Todos los avisos del usuario, del más reciente al más antiguo. */
    List<AvisoApp> findByUsuario(int usuarioId);

    /** Los avisos del usuario enviados desde {@code desdeUtc} (incluido), del más reciente al más antiguo. */
    List<AvisoApp> findByUsuarioDesde(int usuarioId, LocalDateTime desdeUtc);

    /**
     * Los avisos de varios usuarios en una sola consulta, por id de usuario y
     * cada lista del más reciente al más antiguo. Un usuario sin avisos no
     * está en el mapa.
     */
    Map<Integer, List<AvisoApp>> findByUsuarios(Collection<Integer> usuarioIds);

    /** Borrado en bloque al eliminar la cuenta. */
    void deleteByUsuario(int usuarioId);
}
