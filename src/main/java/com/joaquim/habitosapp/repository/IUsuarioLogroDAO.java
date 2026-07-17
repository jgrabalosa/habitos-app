package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioLogro;
import java.util.List;

public interface IUsuarioLogroDAO {
    void save(UsuarioLogro usuarioLogro);
    void deleteByUsuario(int usuarioId);
    UsuarioLogro findById(int id);
    List<UsuarioLogro> findByUsuario(Usuario usuario);
    boolean existePorUsuarioYLogro(int usuarioId, int logroId);
}