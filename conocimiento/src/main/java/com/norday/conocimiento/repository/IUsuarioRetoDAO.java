package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.UsuarioReto;

import java.time.LocalDateTime;
import java.util.List;

public interface IUsuarioRetoDAO {

    void save(UsuarioReto usuarioReto);

    /**
     * La bandeja: los retos del usuario que ya han vencido ({@code venceEn}
     * anterior o igual a {@code ahora}, en UTC), del más antiguo al más nuevo,
     * como mucho {@code limite}. Con el reto cargado. Los retos retirados
     * ({@code activo = false}) no salen: nadie puede responderlos.
     */
    List<UsuarioReto> findVencidos(int usuarioId, LocalDateTime ahora, int limite);

    /** Cuántos retos vencidos tiene el usuario, con el mismo criterio que {@link #findVencidos}. */
    long contarVencidos(int usuarioId, LocalDateTime ahora);

    /** Todos los repasos del usuario, con su reto cargado: para la exportación RGPD. */
    List<UsuarioReto> findByUsuario(int usuarioId);

    /** Borrado en bloque al eliminar la cuenta. Ver LimpiadorConocimiento. */
    void deleteByUsuario(int usuarioId);
}
