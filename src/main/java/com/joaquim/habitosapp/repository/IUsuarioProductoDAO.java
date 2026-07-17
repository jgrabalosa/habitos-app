package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioProducto;
import java.util.List;

public interface IUsuarioProductoDAO {
    void save(UsuarioProducto usuarioProducto);
    void deleteByUsuario(int usuarioId);
    UsuarioProducto findById(int id);
    List<UsuarioProducto> findByUsuario(Usuario usuario);
    UsuarioProducto findByUsuarioYProducto(int usuarioId, int productoId);
    void update(UsuarioProducto usuarioProducto);
}