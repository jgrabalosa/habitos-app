package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;
import com.joaquim.habitosapp.repository.IRachaDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class RachaDAO implements IRachaDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Racha racha) {
        em.persist(racha);
    }

    @Override
    public Racha findByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT r FROM Racha r WHERE r.habito = :habito", Racha.class)
                .setParameter("habito", habito)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void update(Racha racha) {
        em.merge(racha);
    }
}