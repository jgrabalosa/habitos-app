package com.norday.habitos.repository.impl;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HitoPagado;
import com.norday.habitos.repository.IHitoPagadoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Repository
@Transactional
public class HitoPagadoDAO implements IHitoPagadoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(HitoPagado hito) {
        em.persist(hito);
    }

    @Override
    public List<Integer> findUmbralesByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT h.umbral FROM HitoPagado h WHERE h.habito = :habito", Integer.class)
                .setParameter("habito", habito)
                .getResultList();
    }

    // Uno a uno con remove, no con un DELETE en bloque: al deshacer, el
    // registro se borra después con em.remove, y un DELETE de JPQL no saca
    // del contexto de persistencia las filas que ya estuvieran cargadas, que
    // seguirían apuntando a un registro borrado al hacer flush.
    @Override
    public void deleteByRegistro(int registroId) {
        em.createQuery(
                        "SELECT h FROM HitoPagado h WHERE h.registro.registroId = :registroId",
                        HitoPagado.class)
                .setParameter("registroId", registroId)
                .getResultList()
                .forEach(em::remove);
    }

    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery("DELETE FROM HitoPagado h WHERE h.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
}
