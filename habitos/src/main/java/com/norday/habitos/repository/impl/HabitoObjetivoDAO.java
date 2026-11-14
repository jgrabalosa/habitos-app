package com.norday.habitos.repository.impl;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Transactional
public class HabitoObjetivoDAO implements IHabitoObjetivoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(HabitoObjetivo version) {
        em.persist(version);
    }

    @Override
    public void update(HabitoObjetivo version) {
        em.merge(version);
    }

    // Con remove y no con un DELETE de JPQL: quien borra una versión
    // programada puede tener cargadas las demás, y un DELETE en bloque no
    // las saca del contexto de persistencia.
    @Override
    public void delete(int id) {
        HabitoObjetivo version = em.find(HabitoObjetivo.class, id);
        if (version != null) {
            em.remove(version);
        }
    }

    @Override
    public List<HabitoObjetivo> findByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT o FROM HabitoObjetivo o WHERE o.habito = :habito ORDER BY o.desde",
                        HabitoObjetivo.class)
                .setParameter("habito", habito)
                .getResultList();
    }

    // El id del hábito sale en la propia consulta: la asociación es LAZY y
    // leerlo del proxy fuera de una transacción podría reventar.
    @Override
    public Map<Integer, List<HabitoObjetivo>> findByHabitos(List<Habito> habitos) {
        Map<Integer, List<HabitoObjetivo>> porHabito = new HashMap<>();
        if (habitos.isEmpty()) {
            return porHabito;
        }
        List<Object[]> filas = em.createQuery(
                        "SELECT o.habito.habitoId, o FROM HabitoObjetivo o "
                                + "WHERE o.habito IN :habitos ORDER BY o.desde", Object[].class)
                .setParameter("habitos", habitos)
                .getResultList();
        for (Object[] fila : filas) {
            porHabito.computeIfAbsent((Integer) fila[0], id -> new ArrayList<>())
                    .add((HabitoObjetivo) fila[1]);
        }
        return porHabito;
    }

    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery("DELETE FROM HabitoObjetivo o WHERE o.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
}
