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

    /** Tope de espera de los bloqueos (ver bloquear). */
    @org.springframework.beans.factory.annotation.Value("${norday.bloqueo.espera-maxima:10s}")
    private String esperaMaximaBloqueo;

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
        // Con el mismo tope que bloquear: este UPDATE espera a quien tenga al
        // usuario bloqueado, y va en la primera petición del día, antes de
        // llegar al controlador. Si agota el tope, UltimoAccesoService se lo
        // traga y la petición sigue.
        em.createNativeQuery("SELECT set_config('lock_timeout', :espera, true)")
                .setParameter("espera", esperaMaximaBloqueo)
                .getSingleResult();
        em.createQuery("UPDATE Usuario u SET u.ultimoAcceso = :ahora WHERE u.usuarioId = :id")
                .setParameter("ahora", ahora)
                .setParameter("id", usuarioId)
                .executeUpdate();
    }

    // FOR NO KEY UPDATE y no PESSIMISTIC_WRITE, que en Postgres es FOR
    // UPDATE: así no frena a otras transacciones que inserten filas que
    // apunten al usuario (la comprobación de la FK toma FOR KEY SHARE, que es
    // compatible), solo a las que pidan este mismo bloqueo, modifiquen la
    // fila o la borren.
    @Override
    public boolean bloquear(int usuarioId) {
        // Con tope: lock_timeout de esta transacción (set_config local), sin
        // depender de cómo esté configurada la base de datos. Si salta, la
        // espera acaba en LockTimeoutException y la API responde 503
        // (GlobalExceptionHandler). Vale para el resto de la transacción.
        em.createNativeQuery("SELECT set_config('lock_timeout', :espera, true)")
                .setParameter("espera", esperaMaximaBloqueo)
                .getSingleResult();
        return !em.createNativeQuery("SELECT usuario_id FROM usuario WHERE usuario_id = :id FOR NO KEY UPDATE")
                .setParameter("id", usuarioId)
                .getResultList()
                .isEmpty();
    }

    @Override
    public Usuario bloquearYReleer(int usuarioId) {
        boolean existe = bloquear(usuarioId);
        // find devuelve la copia de la sesión si la hay, sin consultar.
        Usuario usuario = em.find(Usuario.class, usuarioId);
        if (!existe) {
            if (usuario != null) em.detach(usuario);
            return null;
        }
        em.refresh(usuario);
        return usuario;
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