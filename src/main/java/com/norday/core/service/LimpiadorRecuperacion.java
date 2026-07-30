package com.norday.core.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.ICodigoRecuperacionDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Borra los códigos de recuperación de contraseña de un usuario.
 *
 * A diferencia del resto de limpiadores, este borra por EMAIL: la tabla
 * codigo_recuperacion no tiene FK a Usuario (guarda el email en claro,
 * porque se pide un código antes de estar autenticado), así que sus filas
 * no caen con el borrado del Usuario y sobrevivirían indefinidamente.
 *
 * Por eso depende de que el email siga disponible cuando se invoca:
 * UsuarioService.eliminarCuenta() pasa el Usuario ya cargado y ejecuta todos
 * los limpiadores antes de borrar su fila, así que el email está intacto aquí.
 */
@Component
public class LimpiadorRecuperacion implements LimpiadorDatosUsuario {

    @Autowired
    private ICodigoRecuperacionDAO codigoRecuperacionDAO;

    @Override
    public void limpiar(Usuario usuario) {
        codigoRecuperacionDAO.deleteByEmail(usuario.getEmail());
    }
}
