package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Producto;
import com.joaquim.habitosapp.repository.IProductoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class ProductoDAO implements IProductoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Producto producto) {
        em.persist(producto);
    }

    @Override
    public Producto findById(int id) {
        return em.find(Producto.class, id);
    }

    @Override
    public List<Producto> findAll() {
        return em.createQuery("SELECT p FROM Producto p ORDER BY p.categoria, p.nombre", Producto.class)
                .getResultList();
    }

    @Override
    public List<Producto> findActivos() {
        return em.createQuery(
                        "SELECT p FROM Producto p WHERE p.activo = true ORDER BY p.categoria, p.nombre", Producto.class)
                .getResultList();
    }

    @Override
    public void update(Producto producto) {
        em.merge(producto);
    }

    @Override
    public void delete(int id) {
        Producto producto = findById(id);
        if (producto != null) {
            em.remove(producto);
        }
    }
}