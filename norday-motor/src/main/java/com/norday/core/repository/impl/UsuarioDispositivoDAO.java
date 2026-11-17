package com.norday.core.repository.impl;

import com.norday.core.model.UsuarioDispositivo;
import com.norday.core.repository.IUsuarioDispositivoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Transactional
public class UsuarioDispositivoDAO implements IUsuarioDispositivoDAO {

    @PersistenceContext
    private EntityManager em;

    // Nativa con ON CONFLICT DO UPDATE, como HitoPagadoDAO con su DO NOTHING:
    // la app manda el token al arrancar y al volver a primer plano, y dos PUT
    // casi a la vez del mismo usuario y app chocarían con la restricción única
    // si se hiciera buscar-y-después-insertar. Así el segundo espera al primero
    // y escribe encima.
    //
    // Antes, el mismo token de esa app deja de ser de cualquier otro usuario:
    // un token es una instalación, y si llega para otro usuario es que en ese
    // móvil se cerró sesión y entró otra persona. Si siguiera en el anterior,
    // sus avisos llegarían al móvil de quien ha entrado.
    @Override
    public void guardar(int usuarioId, String appId, String fcmToken) {
        em.createNativeQuery(
                        "DELETE FROM usuario_dispositivo "
                                + "WHERE app_id = :app AND fcm_token = :token AND usuario_ref <> :usuario")
                .setParameter("usuario", usuarioId)
                .setParameter("app", appId)
                .setParameter("token", fcmToken)
                .executeUpdate();
        em.createNativeQuery(
                        "INSERT INTO usuario_dispositivo (usuario_ref, app_id, fcm_token, actualizado_en) "
                                + "VALUES (:usuario, :app, :token, :ahora) "
                                + "ON CONFLICT (usuario_ref, app_id) DO UPDATE "
                                + "SET fcm_token = EXCLUDED.fcm_token, actualizado_en = EXCLUDED.actualizado_en")
                .setParameter("usuario", usuarioId)
                .setParameter("app", appId)
                .setParameter("token", fcmToken)
                .setParameter("ahora", LocalDateTime.now(ZoneOffset.UTC))
                .executeUpdate();
    }

    @Override
    public String tokenDe(int usuarioId, String appId) {
        return em.createQuery(
                        "SELECT d.fcmToken FROM UsuarioDispositivo d "
                                + "WHERE d.usuario.usuarioId = :usuario AND d.appId = :app", String.class)
                .setParameter("usuario", usuarioId)
                .setParameter("app", appId)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public Map<Integer, String> tokensDe(Collection<Integer> usuarioIds, String appId) {
        Map<Integer, String> tokens = new HashMap<>();
        if (usuarioIds.isEmpty()) {
            return tokens;
        }
        List<Object[]> filas = em.createQuery(
                        "SELECT d.usuario.usuarioId, d.fcmToken FROM UsuarioDispositivo d "
                                + "WHERE d.usuario.usuarioId IN :usuarios AND d.appId = :app", Object[].class)
                .setParameter("usuarios", usuarioIds)
                .setParameter("app", appId)
                .getResultList();
        for (Object[] fila : filas) {
            tokens.put((Integer) fila[0], (String) fila[1]);
        }
        return tokens;
    }

    @Override
    public void borrar(int usuarioId, String appId) {
        em.createQuery(
                        "DELETE FROM UsuarioDispositivo d "
                                + "WHERE d.usuario.usuarioId = :usuario AND d.appId = :app")
                .setParameter("usuario", usuarioId)
                .setParameter("app", appId)
                .executeUpdate();
    }

    @Override
    public boolean borrarSiCoincide(int usuarioId, String appId, String fcmToken) {
        int filas = em.createQuery(
                        "DELETE FROM UsuarioDispositivo d "
                                + "WHERE d.usuario.usuarioId = :usuario AND d.appId = :app "
                                + "AND d.fcmToken = :token")
                .setParameter("usuario", usuarioId)
                .setParameter("app", appId)
                .setParameter("token", fcmToken)
                .executeUpdate();
        return filas == 1;
    }

    @Override
    public List<UsuarioDispositivo> findByUsuario(int usuarioId) {
        return em.createQuery(
                        "SELECT d FROM UsuarioDispositivo d WHERE d.usuario.usuarioId = :usuario "
                                + "ORDER BY d.appId", UsuarioDispositivo.class)
                .setParameter("usuario", usuarioId)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioDispositivo d WHERE d.usuario.usuarioId = :usuario")
                .setParameter("usuario", usuarioId)
                .executeUpdate();
    }
}
