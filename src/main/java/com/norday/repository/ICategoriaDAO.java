package com.norday.repository;

import com.norday.model.Categoria;
import com.norday.model.Usuario;
import java.util.List;

public interface ICategoriaDAO {

    void save(Categoria categoria);
    Categoria findById(int id);
    List<Categoria> findGlobales();
    List<Categoria> findByCreador(Usuario creador);
    List<Categoria> findAll(Usuario creador);
    void update(Categoria categoria);
    void delete(int id);
}