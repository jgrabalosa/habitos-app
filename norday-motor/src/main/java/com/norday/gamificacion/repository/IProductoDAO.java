package com.norday.gamificacion.repository;

import com.norday.gamificacion.model.Producto;
import java.util.List;

public interface IProductoDAO {
    void save(Producto producto);
    Producto findById(int id);
    Producto findByCodigo(String codigo);
    List<Producto> findAll();
    List<Producto> findActivos();
    List<Producto> findActivosParaApp(String appId);
    void update(Producto producto);
    void delete(int id);
}