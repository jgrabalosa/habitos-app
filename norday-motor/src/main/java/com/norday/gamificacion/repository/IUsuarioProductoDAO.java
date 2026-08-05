package com.norday.gamificacion.repository;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.UsuarioProducto;
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