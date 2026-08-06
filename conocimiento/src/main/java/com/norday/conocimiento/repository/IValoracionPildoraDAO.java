package com.norday.conocimiento.repository;

import com.norday.conocimiento.model.ValoracionPildora;
import com.norday.core.model.Usuario;

public interface IValoracionPildoraDAO {

    /** Puede devolver null: valorar es opcional. */
    ValoracionPildora findByUsuarioYPildora(Usuario usuario, int pildoraId);

    void save(ValoracionPildora valoracion);
    void update(ValoracionPildora valoracion);
}
