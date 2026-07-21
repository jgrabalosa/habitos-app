package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioProducto;
import com.joaquim.habitosapp.repository.IUsuarioProductoDAO;
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

    @Override
    public UsuarioProducto findEquipadoPorCategoria(int usuarioId, String categoria) {
        try {
            return em.createQuery(
                            "SELECT up FROM UsuarioProducto up " +
                                    "WHERE up.usuario.usuarioId = :usuarioId " +
                                    "AND up.producto.categoria = :categoria " +
                                    "AND up.equipado = true",
                            UsuarioProducto.class)
                    .setParameter("usuarioId", usuarioId)
                    .setParameter("categoria", categoria)
                    .getSingleResult();
        } catch (NoResultException e) {
            return null;
        }
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