package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioMoneda;
import com.joaquim.habitosapp.repository.IUsuarioMonedaDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class UsuarioMonedaDAO implements IUsuarioMonedaDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(UsuarioMoneda movimiento) {
        em.persist(movimiento);
    }

    @Override
    public List<UsuarioMoneda> findByUsuario(Usuario usuario) {
        return em.createQuery(
                        "SELECT m FROM UsuarioMoneda m WHERE m.usuario = :usuario ORDER BY m.fecha DESC",
                        UsuarioMoneda.class)
                .setParameter("usuario", usuario)
                .getResultList();
    }

    @Override
    public int calcularSaldo(int usuarioId) {
        Long saldo = em.createQuery(
                        "SELECT COALESCE(SUM(m.cantidad), 0) FROM UsuarioMoneda m WHERE m.usuario.usuarioId = :usuarioId",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
        return saldo.intValue();
    }
}