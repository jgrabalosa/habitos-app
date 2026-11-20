package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.RespuestaReto;
import com.norday.conocimiento.repository.IRespuestaRetoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Repository
@Transactional
public class RespuestaRetoDAO implements IRespuestaRetoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(RespuestaReto respuesta) {
        em.persist(respuesta);
    }

    @Override
    public List<RespuestaReto> findByUsuarioYDia(int usuarioId, LocalDate dia) {
        return em.createQuery(
                        "SELECT r FROM RespuestaReto r " +
                        "WHERE r.usuario.usuarioId = :usuarioId AND r.dia = :dia " +
                        "ORDER BY r.respondidaEn, r.id",
                        RespuestaReto.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("dia", dia)
                .getResultList();
    }

    @Override
    public List<RespuestaReto> findByUsuario(int usuarioId) {
        return em.createQuery(
                        "SELECT r FROM RespuestaReto r JOIN FETCH r.reto " +
                        "WHERE r.usuario.usuarioId = :usuarioId ORDER BY r.id",
                        RespuestaReto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM RespuestaReto r WHERE r.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
