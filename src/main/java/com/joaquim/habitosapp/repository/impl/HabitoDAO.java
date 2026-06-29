package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class HabitoDAO implements IHabitoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Habito habito) {
        em.persist(habito);
    }

    @Override
    public Habito findById(int id) {
        return em.find(Habito.class, id);
    }

    @Override
    public List<Habito> findByPropietario(Usuario propietario) {
        return em.createQuery(
                        "SELECT h FROM Habito h WHERE h.propietario = :propietario ORDER BY h.nombre", Habito.class)
                .setParameter("propietario", propietario)
                .getResultList();
    }

    @Override
    public List<Habito> findActivos(Usuario propietario) {
        return em.createQuery(
                        "SELECT h FROM Habito h WHERE h.propietario = :propietario AND h.activo = true ORDER BY h.nombre", Habito.class)
                .setParameter("propietario", propietario)
                .getResultList();
    }

    @Override
    public List<Habito> findInactivos(Usuario propietario) {
        return em.createQuery(
                        "SELECT h FROM Habito h WHERE h.propietario = :propietario AND h.activo = false ORDER BY h.nombre", Habito.class)
                .setParameter("propietario", propietario)
                .getResultList();
    }

    @Override
    public void update(Habito habito) {
        em.merge(habito);
    }

    @Override
    public void delete(int id) {
        Habito habito = findById(id);
        if (habito != null) {
            em.remove(habito);
        }
    }
}