package com.norday.gamificacion.repository;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.UsuarioMoneda;
import java.util.List;

public interface IUsuarioMonedaDAO {
    void save(UsuarioMoneda movimiento);
    void deleteByUsuario(int usuarioId);
    List<UsuarioMoneda> findByUsuario(Usuario usuario);
    int calcularSaldo(int usuarioId);
}