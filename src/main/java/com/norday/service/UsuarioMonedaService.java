package com.norday.service;

import com.norday.model.Usuario;
import com.norday.model.UsuarioMoneda;
import com.norday.repository.IUsuarioMonedaDAO;
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