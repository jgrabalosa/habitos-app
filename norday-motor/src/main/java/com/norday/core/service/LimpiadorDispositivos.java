package com.norday.core.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDispositivoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Borra los tokens de notificaciones del usuario en todas las apps
 * (usuario_dispositivo). La tabla solo cuelga de Usuario, así que no importa
 * en qué orden corra respecto a los demás limpiadores.
 */
@Component
public class LimpiadorDispositivos implements LimpiadorDatosUsuario {

    @Autowired
    private IUsuarioDispositivoDAO dispositivoDAO;

    @Override
    public void limpiar(Usuario usuario) {
        dispositivoDAO.deleteByUsuario(usuario.getUsuarioId());
    }
}
