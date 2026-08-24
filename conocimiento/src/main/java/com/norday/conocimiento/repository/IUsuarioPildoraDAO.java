package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.core.model.Usuario;
import java.time.LocalDateTime;
import java.util.List;

public interface IUsuarioPildoraDAO {

    /** Puede devolver null: el par usuario-píldora solo existe tras la primera interacción. */
    UsuarioPildora findByUsuarioYPildora(Usuario usuario, int pildoraId);

    void save(UsuarioPildora usuarioPildora);
    void update(UsuarioPildora usuarioPildora);

    /** Cuántas píldoras ha tocado el usuario después de un instante dado. */
    long contarInteraccionesDesde(Usuario usuario, LocalDateTime fecha);

    /**
     * Lo que el usuario ha leído, para la pantalla Colección. Ambos filtros son
     * opcionales (null = sin filtrar). Lo descartado nunca sale: no es suyo.
     */
    List<UsuarioPildora> findParaColeccion(Usuario usuario, Integer categoriaId, EstadoPildora estado);

    /** Borrado en bloque al eliminar la cuenta. Ver LimpiadorConocimiento. */
    void deleteByUsuario(int usuarioId);
}
