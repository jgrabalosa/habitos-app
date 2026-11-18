package com.norday.gamificacion.repository.impl;

import com.norday.gamificacion.model.Oferta;
import com.norday.gamificacion.model.dto.OfertaDTO;
import com.norday.gamificacion.repository.IOfertaDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;

@Repository
@Transactional
public class OfertaDAO implements IOfertaDAO {

    private static final String SELECT =
            "SELECT new com.norday.gamificacion.model.dto.OfertaDTO("
                    + "o.producto.productoId, o.desde, o.hasta, o.precio) FROM Oferta o ";

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Oferta oferta) {
        em.persist(oferta);
    }

    @Override
    public List<OfertaDTO> deProducto(int productoId) {
        return em.createQuery(SELECT + "WHERE o.producto.productoId = :id ORDER BY o.desde, o.ofertaId",
                        OfertaDTO.class)
                .setParameter("id", productoId)
                .getResultList();
    }

    @Override
    public List<OfertaDTO> deProductos(Collection<Integer> productoIds) {
        if (productoIds.isEmpty()) {
            return List.of();
        }
        return em.createQuery(SELECT + "WHERE o.producto.productoId IN :ids ORDER BY o.desde, o.ofertaId",
                        OfertaDTO.class)
                .setParameter("ids", productoIds)
                .getResultList();
    }
}
