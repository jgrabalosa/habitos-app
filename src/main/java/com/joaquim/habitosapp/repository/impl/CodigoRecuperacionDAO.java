package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.CodigoRecuperacion;
import com.joaquim.habitosapp.repository.ICodigoRecuperacionDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

@Repository
@Transactional
public class CodigoRecuperacionDAO implements ICodigoRecuperacionDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(CodigoRecuperacion codigo) {
        em.persist(codigo);
    }

    @Override
    public CodigoRecuperacion findVigenteByEmailYCodigo(String email, String codigo) {
        return em.createQuery(
                        "SELECT c FROM CodigoRecuperacion c " +
                                "WHERE c.email = :email AND c.codigo = :codigo " +
                                "AND c.usado = false AND c.fechaExpiracion > :ahora " +
                                "ORDER BY c.fechaExpiracion DESC", CodigoRecuperacion.class)
                .setParameter("email", email)
                .setParameter("codigo", codigo)
                .setParameter("ahora", LocalDateTime.now())
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public void update(CodigoRecuperacion codigo) {
        em.merge(codigo);
    }

    @Override
    public void invalidarCodigosDeEmail(String email) {
        em.createQuery(
                        "UPDATE CodigoRecuperacion c SET c.usado = true " +
                                "WHERE c.email = :email AND c.usado = false")
                .setParameter("email", email)
                .executeUpdate();
    }
}