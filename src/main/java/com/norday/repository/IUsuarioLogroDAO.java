package com.norday.repository;

import com.norday.model.Usuario;
import com.norday.model.UsuarioLogro;
import java.util.List;

public interface IUsuarioLogroDAO {
    void save(UsuarioLogro usuarioLogro);
    void deleteByUsuario(int usuarioId);
    UsuarioLogro findById(int id);
    List<UsuarioLogro> findByUsuario(Usuario usuario);
    boolean existePorUsuarioYLogro(int usuarioId, int logroId);
}