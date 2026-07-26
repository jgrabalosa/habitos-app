package com.norday.service;

import com.norday.model.Categoria;
import com.norday.model.Habito;
import com.norday.model.Usuario;
import com.norday.repository.ICategoriaDAO;
import com.norday.repository.IHabitoDAO;
import com.norday.repository.IRachaDAO;
import com.norday.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Borra los datos del dominio "hábitos" de un usuario. El orden interno
 * importa: registros y rachas antes que los hábitos de los que cuelgan.
 */
@Component
public class LimpiadorHabitos implements LimpiadorDatosUsuario {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Override
    public void limpiar(Usuario usuario) {
        // 1. Borrar registros y rachas de todos sus hábitos
        List<Habito> habitos = habitoDAO.findByPropietario(usuario);
        for (Habito habito : habitos) {
            registroDAO.deleteByHabito(habito.getHabitoId());
            rachaDAO.deleteByHabito(habito.getHabitoId());
        }

        // 2. Borrar los hábitos
        for (Habito habito : habitos) {
            habitoDAO.delete(habito.getHabitoId());
        }

        // 3. Borrar categorías personalizadas del usuario
        List<Categoria> categorias = categoriaDAO.findByCreador(usuario);
        for (Categoria categoria : categorias) {
            categoriaDAO.delete(categoria.getCategoriaId());
        }
    }
}
