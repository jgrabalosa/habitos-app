package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioMoneda;
import com.joaquim.habitosapp.repository.IUsuarioMonedaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class UsuarioMonedaService {

    @Autowired
    private IUsuarioMonedaDAO usuarioMonedaDAO;

    public void registrarMovimiento(Usuario usuario, int cantidad, String origen,
                                    Integer referenciaId, String descripcion) {
        UsuarioMoneda movimiento = new UsuarioMoneda(usuario, cantidad, origen, referenciaId, descripcion);
        usuarioMonedaDAO.save(movimiento);
    }

    public int consultarSaldo(int usuarioId) {
        return usuarioMonedaDAO.calcularSaldo(usuarioId);
    }
}