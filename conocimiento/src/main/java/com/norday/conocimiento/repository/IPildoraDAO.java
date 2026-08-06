package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.PildoraCategoria;
import com.norday.core.model.Usuario;
import java.util.List;

public interface IPildoraDAO {

    Pildora findById(int id);

    /**
     * Píldoras activas de una categoría que el usuario aún no ha leído.
     *
     * Deja pasar a propósito las que descartó: la reaparición (3 strikes y
     * las 10 interacciones de espera) se decide en Java, en el servicio, con
     * datos que esta consulta no tiene por qué conocer.
     */
    List<Pildora> findActivasElegibles(Usuario usuario, int categoriaId);

    /** La relación marcada como principal, para saber a qué categoría sumar afinidad. */
    PildoraCategoria findCategoriaPrincipal(int pildoraId);

    List<Categoria> findCategorias(int pildoraId);
}
