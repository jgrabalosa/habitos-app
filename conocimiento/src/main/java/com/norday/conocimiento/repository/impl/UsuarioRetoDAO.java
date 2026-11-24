package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.UsuarioReto;
import com.norday.conocimiento.repository.IUsuarioRetoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Repository
@Transactional
public class UsuarioRetoDAO implements IUsuarioRetoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(UsuarioReto usuarioReto) {
        em.persist(usuarioReto);
    }

    /** El id desempata: dos retos que vencen en el mismo instante salen siempre en el mismo orden. */
    @Override
    public List<UsuarioReto> findVencidos(int usuarioId, LocalDateTime ahora, int limite) {
        return em.createQuery(
                        "SELECT ur FROM UsuarioReto ur JOIN FETCH ur.reto r " +
                        "WHERE ur.usuario.usuarioId = :usuarioId AND ur.venceEn <= :ahora AND r.activo = true " +
                        "ORDER BY ur.venceEn, ur.id",
                        UsuarioReto.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("ahora", ahora)
                .setMaxResults(limite)
                .getResultList();
    }

    @Override
    public long contarVencidos(int usuarioId, LocalDateTime ahora) {
        return em.createQuery(
                        "SELECT COUNT(ur) FROM UsuarioReto ur " +
                        "WHERE ur.usuario.usuarioId = :usuarioId AND ur.venceEn <= :ahora AND ur.reto.activo = true",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("ahora", ahora)
                .getSingleResult();
    }

    @Override
    public List<UsuarioReto> findByUsuario(int usuarioId) {
        return em.createQuery(
                        "SELECT ur FROM UsuarioReto ur JOIN FETCH ur.reto " +
                        "WHERE ur.usuario.usuarioId = :usuarioId ORDER BY ur.id",
                        UsuarioReto.class)
                .setParameter("usuarioId", usuarioId)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioReto ur WHERE ur.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
