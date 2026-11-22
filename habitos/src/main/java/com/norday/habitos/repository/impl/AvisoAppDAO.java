package com.norday.habitos.repository.impl;

import com.norday.habitos.model.AvisoApp;
import com.norday.habitos.repository.IAvisoAppDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Transactional
public class AvisoAppDAO implements IAvisoAppDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public AvisoApp save(AvisoApp aviso) {
        em.persist(aviso);
        return aviso;
    }

    @Override
    public List<AvisoApp> findByUsuario(int usuarioId) {
        return em.createQuery(
                        "SELECT a FROM AvisoApp a WHERE a.usuario.usuarioId = :usuarioId "
                                + "ORDER BY a.enviadoEn DESC, a.id DESC", AvisoApp.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    public List<AvisoApp> findByUsuarioDesde(int usuarioId, LocalDateTime desdeUtc) {
        return em.createQuery(
                        "SELECT a FROM AvisoApp a WHERE a.usuario.usuarioId = :usuarioId "
                                + "AND a.enviadoEn >= :desde "
                                + "ORDER BY a.enviadoEn DESC, a.id DESC", AvisoApp.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("desde", desdeUtc)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM AvisoApp a WHERE a.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
