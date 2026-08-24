package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.ValoracionPildora;
import com.norday.conocimiento.repository.IValoracionPildoraDAO;
import com.norday.core.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class ValoracionPildoraDAO implements IValoracionPildoraDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public ValoracionPildora findByUsuarioYPildora(Usuario usuario, int pildoraId) {
        return em.createQuery(
                        "SELECT v FROM ValoracionPildora v " +
                        "WHERE v.usuario = :usuario AND v.pildora.pildoraId = :pildoraId",
                        ValoracionPildora.class)
                .setParameter("usuario", usuario)
                .setParameter("pildoraId", pildoraId)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    public void save(ValoracionPildora valoracion) {
        em.persist(valoracion);
    }

    @Override
    public void update(ValoracionPildora valoracion) {
        em.merge(valoracion);
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM ValoracionPildora v WHERE v.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
