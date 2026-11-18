package com.norday.gamificacion.repository.impl;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class UsuarioProductoDAO implements IUsuarioProductoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(UsuarioProducto usuarioProducto) {
        em.persist(usuarioProducto);
    }

    @Override
    public UsuarioProducto findById(int id) {
        return em.find(UsuarioProducto.class, id);
    }

    @Override
    public List<UsuarioProducto> findByUsuario(Usuario usuario) {
        return em.createQuery(
                        "SELECT up FROM UsuarioProducto up WHERE up.usuario = :usuario ORDER BY up.fechaAdquirido DESC",
                        UsuarioProducto.class)
                .setParameter("usuario", usuario)
                .getResultList();
    }

    @Override
    public UsuarioProducto findByUsuarioYProducto(int usuarioId, int productoId) {
        try {
            return em.createQuery(
                            "SELECT up FROM UsuarioProducto up WHERE up.usuario.usuarioId = :usuarioId AND up.producto.productoId = :productoId",
                            UsuarioProducto.class)
                    .setParameter("usuarioId", usuarioId)
                    .setParameter("productoId", productoId)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
    }

    /**
     * Lo puesto en esa categoría, de usuario_equipado (la fuente de verdad,
     * V29): la clave primaria (usuario, categoría) impide que haya dos.
     */
    @Override
    public UsuarioProducto findEquipadoPorCategoria(int usuarioId, String categoria) {
        return em.createQuery(
                        "SELECT ue.usuarioProducto FROM UsuarioEquipado ue "
                                + "WHERE ue.id.usuarioId = :usuarioId AND ue.id.categoria = :categoria",
                        UsuarioProducto.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("categoria", categoria)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public boolean poseeAlgunoDeCategoria(int usuarioId, String categoria) {
        Long total = em.createQuery(
                        "SELECT COUNT(up) FROM UsuarioProducto up " +
                                "WHERE up.usuario.usuarioId = :usuarioId " +
                                "AND up.producto.categoria = :categoria",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("categoria", categoria)
                .getSingleResult();
        return total != null && total > 0;
    }

    @Override
    public void update(UsuarioProducto usuarioProducto) {
        em.merge(usuarioProducto);
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioProducto up WHERE up.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}