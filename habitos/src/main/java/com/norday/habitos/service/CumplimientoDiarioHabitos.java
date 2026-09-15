package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.gamificacion.service.CumplimientoDiarioPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Cómo cumple hoy el usuario en el dominio "hábitos": todos los DIARIO
 * activos completados. Delega en {@link HabitoService#esDiaCompleto}, que ya
 * conoce esa regla; aquí solo se resuelve el {@link Usuario} a partir del id
 * que pide el motor.
 */
@Service
public class CumplimientoDiarioHabitos implements CumplimientoDiarioPort {

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Override
    public boolean hoyCumplido(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return false;
        return habitoService.esDiaCompleto(usuario);
    }
}
