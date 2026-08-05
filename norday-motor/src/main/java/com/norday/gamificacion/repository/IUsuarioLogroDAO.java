package com.norday.gamificacion.repository;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.UsuarioLogro;
import java.util.List;

public interface IUsuarioLogroDAO {
    void save(UsuarioLogro usuarioLogro);
    void deleteByUsuario(int usuarioId);
    UsuarioLogro findById(int id);
    List<UsuarioLogro> findByUsuario(Usuario usuario);
    boolean existePorUsuarioYLogro(int usuarioId, int logroId);
}