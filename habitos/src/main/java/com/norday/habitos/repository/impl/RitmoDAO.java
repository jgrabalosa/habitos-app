package com.norday.habitos.repository.impl;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.IRitmoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class RitmoDAO implements IRitmoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Ritmo ritmo) {
        em.persist(ritmo);
    }

    @Override
    public Ritmo findByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT r FROM Ritmo r WHERE r.habito = :habito", Ritmo.class)
                .setParameter("habito", habito)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void update(Ritmo ritmo) {
        em.merge(ritmo);
    }

    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery("DELETE FROM Ritmo r WHERE r.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
}
