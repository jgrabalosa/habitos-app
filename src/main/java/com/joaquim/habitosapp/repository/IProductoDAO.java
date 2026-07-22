package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Producto;
import java.util.List;

public interface IProductoDAO {
    void save(Producto producto);
    Producto findById(int id);
    Producto findByCodigo(String codigo);
    List<Producto> findAll();
    List<Producto> findActivos();
    void update(Producto producto);
    void delete(int id);
}