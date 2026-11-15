package com.norday.enfoque.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.LimpiadorDatosUsuario;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Borra las sesiones de Norday Focus del usuario al eliminarse su cuenta.
 *
 * sesion_enfoque tiene FK NOT NULL a usuario sin borrado en cascada: sin este
 * limpiador, la baja fallaría en cuanto el usuario tuviera una sola sesión. La
 * tabla solo cuelga de usuario (destino_ref es opaco, sin FK), así que da igual
 * en qué orden corra respecto a los demás limpiadores.
 *
 * Los minutos que una sesión llevó a su destino son datos del destino y los
 * borra su propio limpiador.
 */
@Component
public class LimpiadorEnfoque implements LimpiadorDatosUsuario {

    @Autowired
    private ISesionEnfoqueDAO sesionDAO;

    @Override
    public void limpiar(Usuario usuario) {
        sesionDAO.deleteByUsuario(usuario.getUsuarioId());
    }
}
