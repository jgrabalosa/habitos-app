package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Logro;
import com.joaquim.habitosapp.repository.ILogroDAO;
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
}