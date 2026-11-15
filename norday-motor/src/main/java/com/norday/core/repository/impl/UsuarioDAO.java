package com.norday.core.repository.impl;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Transactional
public class UsuarioDAO implements IUsuarioDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Usuario usuario) {
        em.persist(usuario);
    }

    @Override
    public Usuario findById(int id) {
        return em.find(Usuario.class, id);
    }

    @Override
    public Usuario findByEmail(String email) {
        return em.createQuery(
                        "SELECT u FROM Usuario u WHERE u.email = :email", Usuario.class)
                .setParameter("email", email)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Usuario findByUsername(String username) {
        return em.createQuery(
                        "SELECT u FROM Usuario u WHERE u.username = :username", Usuario.class)
                .setParameter("username", username)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public List<Usuario> findAll() {
        return em.createQuery("SELECT u FROM Usuario u", Usuario.class)
                .getResultList();
    }

    @Override
    public void update(Usuario usuario) {
        em.merge(usuario);
    }

    @Override
    public void actualizarUltimoAcceso(int usuarioId, LocalDateTime ahora) {
        em.createQuery("UPDATE Usuario u SET u.ultimoAcceso = :ahora WHERE u.usuarioId = :id")
                .setParameter("ahora", ahora)
                .setParameter("id", usuarioId)
                .executeUpdate();
    }

    @Override
    public Map<Integer, String> fcmTokensDe(Collection<Integer> usuarioIds) {
        Map<Integer, String> tokens = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return tokens;
        }
        List<Object[]> filas = em.createQuery(
                        "SELECT u.usuarioId, u.fcmToken FROM Usuario u "
                                + "WHERE u.usuarioId IN :ids AND u.fcmToken IS NOT NULL", Object[].class)
                .setParameter("ids", usuarioIds)
                .getResultList();
        for (Object[] fila : filas) {
            tokens.put((Integer) fila[0], (String) fila[1]);
        }
        return tokens;
    }

    @Override
    public void quitarFcmTokenDeOtros(int usuarioId, String fcmToken) {
        em.createQuery("UPDATE Usuario u SET u.fcmToken = null "
                        + "WHERE u.fcmToken = :token AND u.usuarioId <> :id")
                .setParameter("token", fcmToken)
                .setParameter("id", usuarioId)
                .executeUpdate();
    }

    @Override
    public void delete(int id) {
        Usuario usuario = findById(id);
        if (usuario != null) {
            em.remove(usuario);
        }
    }
}