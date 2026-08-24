package com.norday.habitos.repository.impl;

import com.norday.habitos.model.ReversionRegistro;
import com.norday.habitos.repository.IReversionRegistroDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class ReversionRegistroDAO implements IReversionRegistroDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(ReversionRegistro reversion) {
        em.persist(reversion);
    }

    @Override
    public ReversionRegistro findByRegistro(int registroId) {
        return em.createQuery(
                        "SELECT r FROM ReversionRegistro r WHERE r.registro.registroId = :registroId",
                        ReversionRegistro.class)
                .setParameter("registroId", registroId)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    public void delete(int reversionId) {
        ReversionRegistro reversion = em.find(ReversionRegistro.class, reversionId);
        if (reversion != null) {
            em.remove(reversion);
        }
    }

    // Los DELETE en bloque de JPQL no respetan cascade, así que hay que borrar
    // primero los ReversionLogro y después los ReversionRegistro.
    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery(
                        "DELETE FROM ReversionLogro rl WHERE rl.reversion IN " +
                        "(SELECT r FROM ReversionRegistro r WHERE r.registro.habito.habitoId = :habitoId)")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
        em.createQuery(
                        "DELETE FROM ReversionRegistro r WHERE r.registro.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
}
