package com.norday.repository;

import com.norday.model.Usuario;
import com.norday.model.UsuarioProducto;
import java.util.List;

public interface IUsuarioProductoDAO {
    void save(UsuarioProducto usuarioProducto);
    void deleteByUsuario(int usuarioId);
    UsuarioProducto findById(int id);
    List<UsuarioProducto> findByUsuario(Usuario usuario);
    UsuarioProducto findByUsuarioYProducto(int usuarioId, int productoId);
    UsuarioProducto findEquipadoPorCategoria(int usuarioId, String categoria);
    void update(UsuarioProducto usuarioProducto);
}