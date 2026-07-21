package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Mascota;
import com.joaquim.habitosapp.repository.IMascotaDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class MascotaDAO implements IMascotaDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Mascota mascota) {
        em.persist(mascota);
    }

    @Override
    public void update(Mascota mascota) {
        em.merge(mascota);
    }

    @Override
    public Mascota findByUsuarioId(int usuarioId) {
        try {
            return em.createQuery(
                            "SELECT m FROM Mascota m WHERE m.usuario.usuarioId = :usuarioId", Mascota.class)
                    .setParameter("usuarioId", usuarioId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }
}