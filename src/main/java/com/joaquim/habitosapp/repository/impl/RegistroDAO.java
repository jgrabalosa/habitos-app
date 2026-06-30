package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Registro;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.List;

@Repository
@Transactional
public class RegistroDAO implements IRegistroDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Registro registro) {
        em.persist(registro);
    }

    @Override
    public Registro findById(int id) {
        return em.find(Registro.class, id);
    }

    @Override
    public List<Registro> findByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT r FROM Registro r WHERE r.habito = :habito ORDER BY r.fecha DESC", Registro.class)
                .setParameter("habito", habito)
                .getResultList();
    }

    @Override
    public Registro findByHabitoAndFecha(Habito habito, LocalDate fecha) {
        return em.createQuery(
                        "SELECT r FROM Registro r WHERE r.habito = :habito AND r.fecha = :fecha", Registro.class)
                .setParameter("habito", habito)
                .setParameter("fecha", fecha)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Registro> findByFecha(LocalDate fecha) {
        return em.createQuery(
                        "SELECT r FROM Registro r WHERE r.fecha = :fecha", Registro.class)
                .setParameter("fecha", fecha)
                .getResultList();
    }

    @Override
    public boolean existeRegistroHoy(Habito habito) {
        return findByHabitoAndFecha(habito, LocalDate.now()) != null;
    }

    @Override
    public void delete(int id) {
        Registro registro = findById(id);
        if (registro != null) {
            em.remove(registro);
        }
    }
    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery("DELETE FROM Registro r WHERE r.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
    @Override
    public List<Registro> findByHabitoAndRango(Habito habito, LocalDate desde, LocalDate hasta) {
        return em.createQuery(
                        "SELECT r FROM Registro r WHERE r.habito = :habito AND r.fecha BETWEEN :desde AND :hasta ORDER BY r.fecha ASC", Registro.class)
                .setParameter("habito", habito)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }
    @Override
    public void update(Registro registro) {
        em.merge(registro);
    }
}