package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.Categoria;
import java.util.List;

public interface ICategoriaConocimientoDAO {

    List<Categoria> findAll();
    Categoria findById(int id);
}
