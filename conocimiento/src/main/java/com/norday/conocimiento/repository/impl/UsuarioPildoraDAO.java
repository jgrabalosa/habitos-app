package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.core.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.TypedQuery;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.List;

@Repository
@Transactional
public class UsuarioPildoraDAO implements IUsuarioPildoraDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public UsuarioPildora findByUsuarioYPildora(Usuario usuario, int pildoraId) {
        return em.createQuery(
                        "SELECT up FROM UsuarioPildora up " +
                        "WHERE up.usuario = :usuario AND up.pildora.pildoraId = :pildoraId",
                        UsuarioPildora.class)
                .setParameter("usuario", usuario)
                .setParameter("pildoraId", pildoraId)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    public void save(UsuarioPildora usuarioPildora) {
        em.persist(usuarioPildora);
    }

    @Override
    public void update(UsuarioPildora usuarioPildora) {
        em.merge(usuarioPildora);
    }

    @Override
    public long contarInteraccionesDesde(Usuario usuario, LocalDateTime fecha) {
        return em.createQuery(
                        "SELECT COUNT(up) FROM UsuarioPildora up " +
                        "WHERE up.usuario = :usuario AND up.fechaUltimaInteraccion > :fecha",
                        Long.class)
                .setParameter("usuario", usuario)
                .setParameter("fecha", fecha)
                .getSingleResult();
    }

    /**
     * La consulta se arma por trozos porque los dos filtros son opcionales y
     * meter `(:x IS NULL OR ...)` para cada uno deja un JPQL que nadie quiere
     * leer dentro de seis meses.
     */
    @Override
    public List<UsuarioPildora> findParaColeccion(Usuario usuario, Integer categoriaId, EstadoPildora estado) {
        StringBuilder jpql = new StringBuilder(
                "SELECT up FROM UsuarioPildora up " +
                "WHERE up.usuario = :usuario " +
                "AND up.estado IN (com.norday.conocimiento.model.EstadoPildora.VISTA, " +
                "                  com.norday.conocimiento.model.EstadoPildora.GUARDADA)");

        if (estado != null) {
            jpql.append(" AND up.estado = :estado");
        }
        if (categoriaId != null) {
            jpql.append(" AND EXISTS (SELECT 1 FROM PildoraCategoria pc " +
                        "             WHERE pc.pildora = up.pildora AND pc.categoria.categoriaId = :categoriaId)");
        }
        jpql.append(" ORDER BY up.fechaUltimaInteraccion DESC");

        TypedQuery<UsuarioPildora> query = em.createQuery(jpql.toString(), UsuarioPildora.class)
                .setParameter("usuario", usuario);
        if (estado != null) {
            query.setParameter("estado", estado);
        }
        if (categoriaId != null) {
            query.setParameter("categoriaId", categoriaId);
        }
        return query.getResultList();
    }

    @Override
    public List<UsuarioPildora> findByUsuario(Usuario usuario) {
        return em.createQuery(
                        "SELECT up FROM UsuarioPildora up WHERE up.usuario = :usuario ORDER BY up.id",
                        UsuarioPildora.class)
                .setParameter("usuario", usuario)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioPildora up WHERE up.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
