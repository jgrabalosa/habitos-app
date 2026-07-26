package com.norday.repository;

import com.norday.model.Usuario;
import java.util.List;

public interface IUsuarioDAO {

    void save(Usuario usuario);
    Usuario findById(int id);
    Usuario findByEmail(String email);
    Usuario findByUsername(String username);
    List<Usuario> findAll();
    void update(Usuario usuario);
    void delete(int id);
}