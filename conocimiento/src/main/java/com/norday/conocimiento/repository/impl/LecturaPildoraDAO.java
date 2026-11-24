package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.LecturaPildora;
import com.norday.conocimiento.repository.ILecturaPildoraDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@Transactional
public class LecturaPildoraDAO implements ILecturaPildoraDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(LecturaPildora lectura) {
        em.persist(lectura);
    }

    @Override
    public List<LecturaPildora> findByUsuarioYDia(int usuarioId, LocalDate dia) {
        return em.createQuery(
                        "SELECT l FROM LecturaPildora l " +
                        "WHERE l.usuario.usuarioId = :usuarioId AND l.dia = :dia " +
                        "ORDER BY l.leidaEn, l.id",
                        LecturaPildora.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("dia", dia)
                .getResultList();
    }

    @Override
    public List<LecturaPildora> findByUsuario(int usuarioId) {
        return em.createQuery(
                        "SELECT l FROM LecturaPildora l JOIN FETCH l.pildora " +
                        "WHERE l.usuario.usuarioId = :usuarioId ORDER BY l.id",
                        LecturaPildora.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM LecturaPildora l WHERE l.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
