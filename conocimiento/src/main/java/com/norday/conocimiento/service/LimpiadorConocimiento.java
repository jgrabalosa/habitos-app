package com.norday.conocimiento.service;

import com.norday.conocimiento.repository.ILecturaPildoraDAO;
import com.norday.conocimiento.repository.IRespuestaRetoDAO;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.conocimiento.repository.IUsuarioRetoDAO;
import com.norday.conocimiento.repository.IValoracionPildoraDAO;
import com.norday.core.model.Usuario;
import com.norday.core.service.LimpiadorDatosUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Borra los datos del dominio "conocimiento" de un usuario: el estado de sus
 * píldoras, sus preferencias de categoría, sus valoraciones y, desde V39, sus
 * lecturas, sus repasos y sus respuestas a los retos.
 *
 * Las seis tablas tienen FK NOT NULL a usuario y sin ON DELETE CASCADE, así
 * que sin este limpiador el borrado de la cuenta falla por violación de clave
 * ajena en cuanto el usuario ha tocado una sola píldora.
 *
 * El orden interno es indiferente: las seis cuelgan de Usuario y del
 * contenido (Pildora, Reto o Categoria), pero no unas de otras.
 */
@Component
public class LimpiadorConocimiento implements LimpiadorDatosUsuario {

    @Autowired
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    @Autowired
    private IUsuarioCategoriaPreferenciaDAO usuarioCategoriaPreferenciaDAO;

    @Autowired
    private IValoracionPildoraDAO valoracionPildoraDAO;

    @Autowired
    private ILecturaPildoraDAO lecturaPildoraDAO;

    @Autowired
    private IUsuarioRetoDAO usuarioRetoDAO;

    @Autowired
    private IRespuestaRetoDAO respuestaRetoDAO;

    @Override
    public void limpiar(Usuario usuario) {
        int id = usuario.getUsuarioId();
        usuarioPildoraDAO.deleteByUsuario(id);
        usuarioCategoriaPreferenciaDAO.deleteByUsuario(id);
        valoracionPildoraDAO.deleteByUsuario(id);
        lecturaPildoraDAO.deleteByUsuario(id);
        usuarioRetoDAO.deleteByUsuario(id);
        respuestaRetoDAO.deleteByUsuario(id);
    }
}
