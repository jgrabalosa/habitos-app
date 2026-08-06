package com.norday.gamificacion.repository.impl;

import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.repository.ILogroDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class LogroDAO implements ILogroDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Logro logro) {
        em.persist(logro);
    }

    @Override
    public Logro findById(int id) {
        return em.find(Logro.class, id);
    }

    @Override
    public List<Logro> findAll() {
        return em.createQuery("SELECT l FROM Logro l ORDER BY l.categoria, l.nivel", Logro.class)
                .getResultList();
    }

    @Override
    public List<Logro> findActivos() {
        return em.createQuery(
                        "SELECT l FROM Logro l WHERE l.activo = true ORDER BY l.categoria, l.nivel", Logro.class)
                .getResultList();
    }

    /**
     * Lo compartido (origenApp NULL) más lo exclusivo de esa app. Un logro de
     * otra app queda fuera.
     */
    @Override
    public List<Logro> findActivosParaApp(String appId) {
        return em.createQuery(
                        "SELECT l FROM Logro l WHERE l.activo = true AND (l.origenApp IS NULL OR l.origenApp = :appId) ORDER BY l.categoria, l.nivel", Logro.class)
                .setParameter("appId", appId)
                .getResultList();
    }

    @Override
    public void update(Logro logro) {
        em.merge(logro);
    }

    @Override
    public void delete(int id) {
        Logro logro = findById(id);
        if (logro != null) {
            em.remove(logro);
        }
    }
    @Override
    public Logro findByCodigo(String codigo) {
        try {
            return em.createQuery(
                            "SELECT l FROM Logro l WHERE l.codigo = :codigo", Logro.class)
                    .setParameter("codigo", codigo)
                    .getSingleResult();
        } catch (jakarta.persistence.NoResultException e) {
            return null;
        }
    }
}