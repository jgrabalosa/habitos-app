package com.norday.gamificacion.repository.impl;

import com.norday.gamificacion.repository.IUsuarioEquipadoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional
public class UsuarioEquipadoDAO implements IUsuarioEquipadoDAO {

    @PersistenceContext
    private EntityManager em;

    /**
     * Una sola sentencia: cambiar de prenda en un sitio es actualizar su fila,
     * nunca tener dos (la clave primaria es usuario y sitio). La consulta
     * nativa vacía antes lo pendiente de la sesión, así que la fila de
     * usuario_producto ya existe.
     */
    @Override
    public void poner(int usuarioId, String categoria, int usuarioProductoId) {
        em.createNativeQuery(
                        "INSERT INTO usuario_equipado (usuario_id, categoria, usuario_producto_id) "
                                + "VALUES (:usuarioId, :categoria, :usuarioProductoId) "
                                + "ON CONFLICT (usuario_id, categoria) "
                                + "DO UPDATE SET usuario_producto_id = EXCLUDED.usuario_producto_id")
                .setParameter("usuarioId", usuarioId)
                .setParameter("categoria", categoria)
                .setParameter("usuarioProductoId", usuarioProductoId)
                .executeUpdate();
    }

    @Override
    public void quitar(int usuarioId, String categoria, int usuarioProductoId) {
        em.createQuery("DELETE FROM UsuarioEquipado ue WHERE ue.id.usuarioId = :usuarioId "
                        + "AND ue.id.categoria = :categoria AND ue.usuarioProducto.usuarioProductoId = :up")
                .setParameter("usuarioId", usuarioId)
                .setParameter("categoria", categoria)
                .setParameter("up", usuarioProductoId)
                .executeUpdate();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioEquipado ue WHERE ue.id.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
