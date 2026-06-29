package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Categoria;
import com.joaquim.habitosapp.model.Usuario;
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