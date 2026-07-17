package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioMoneda;
import java.util.List;

public interface IUsuarioMonedaDAO {
    void save(UsuarioMoneda movimiento);
    void deleteByUsuario(int usuarioId);
    List<UsuarioMoneda> findByUsuario(Usuario usuario);
    int calcularSaldo(int usuarioId);
}