package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.LimpiadorDatosUsuario;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Habito;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.List;

/**
 * Borra los datos del dominio "hábitos" de un usuario. El orden interno
 * importa: libro de hitos, registros, rachas, ritmos e historial de
 * objetivos antes que los hábitos
 * de los que cuelgan. El libro de hitos va antes que los registros, porque
 * también apunta a ellos.
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
    private IRitmoDAO ritmoDAO;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private IReversionRegistroDAO reversionRegistroDAO;

    @Autowired
    private IHitoPagadoDAO hitoPagadoDAO;

    @Autowired
    private IHabitoObjetivoDAO objetivoDAO;

    @Override
    public void limpiar(Usuario usuario) {
        // 1. Borrar libro de hitos, registros, rachas, ritmos e historial de
        //    objetivos de todos sus hábitos
        List<Habito> habitos = habitoDAO.findByPropietario(usuario);
        for (Habito habito : habitos) {
            hitoPagadoDAO.deleteByHabito(habito.getHabitoId());
            reversionRegistroDAO.deleteByHabito(habito.getHabitoId());
            registroDAO.deleteByHabito(habito.getHabitoId());
            rachaDAO.deleteByHabito(habito.getHabitoId());
            ritmoDAO.deleteByHabito(habito.getHabitoId());
            objetivoDAO.deleteByHabito(habito.getHabitoId());
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
