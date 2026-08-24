package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.core.model.Usuario;
import java.util.List;

public interface IUsuarioCategoriaPreferenciaDAO {

    List<UsuarioCategoriaPreferencia> findByUsuario(Usuario usuario);
    UsuarioCategoriaPreferencia findByUsuarioYCategoria(Usuario usuario, int categoriaId);
    void save(UsuarioCategoriaPreferencia preferencia);
    void update(UsuarioCategoriaPreferencia preferencia);

    /** Borrado en bloque al eliminar la cuenta. Ver LimpiadorConocimiento. */
    void deleteByUsuario(int usuarioId);
}
