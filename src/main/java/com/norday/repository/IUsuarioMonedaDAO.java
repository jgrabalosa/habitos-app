package com.norday.repository;

import com.norday.model.Usuario;
import com.norday.model.UsuarioMoneda;
import java.util.List;

public interface IUsuarioMonedaDAO {
    void save(UsuarioMoneda movimiento);
    void deleteByUsuario(int usuarioId);
    List<UsuarioMoneda> findByUsuario(Usuario usuario);
    int calcularSaldo(int usuarioId);
}