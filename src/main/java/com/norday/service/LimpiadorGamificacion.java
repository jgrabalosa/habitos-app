package com.norday.service;

import com.norday.model.Usuario;
import com.norday.repository.IMascotaDAO;
import com.norday.repository.IUsuarioLogroDAO;
import com.norday.repository.IUsuarioMonedaDAO;
import com.norday.repository.IUsuarioProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Borra los datos de gamificación de un usuario: logros conseguidos,
 * movimientos de monedas, productos poseídos y su mascota.
 */
@Component
public class LimpiadorGamificacion implements LimpiadorDatosUsuario {

    @Autowired
    private IUsuarioLogroDAO usuarioLogroDAO;

    @Autowired
    private IUsuarioMonedaDAO usuarioMonedaDAO;

    @Autowired
    private IUsuarioProductoDAO usuarioProductoDAO;

    @Autowired
    private IMascotaDAO mascotaDAO;

    @Override
    public void limpiar(Usuario usuario) {
        int id = usuario.getUsuarioId();
        usuarioLogroDAO.deleteByUsuario(id);
        usuarioMonedaDAO.deleteByUsuario(id);
        usuarioProductoDAO.deleteByUsuario(id);
        mascotaDAO.deleteByUsuario(id);
    }
}
