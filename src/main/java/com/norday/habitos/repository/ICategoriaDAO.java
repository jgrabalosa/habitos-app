package com.norday.habitos.repository;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Categoria;
import java.util.List;

public interface ICategoriaDAO {

    void save(Categoria categoria);
    Categoria findById(int id);
    List<Categoria> findGlobales();
    Categoria findByCodigo(String codigo);
    List<Categoria> findByCreador(Usuario creador);
    List<Categoria> findAll(Usuario creador);
    void update(Categoria categoria);
    void delete(int id);
}