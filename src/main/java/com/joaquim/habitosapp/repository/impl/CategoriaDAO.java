package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Categoria;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.ICategoriaDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class CategoriaDAO implements ICategoriaDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Categoria categoria) {
        em.persist(categoria);
    }

    @Override
    public Categoria findById(int id) {
        return em.find(Categoria.class, id);
    }

    @Override
    public List<Categoria> findGlobales() {
        return em.createQuery(
                        "SELECT c FROM Categoria c WHERE c.esGlobal = true ORDER BY c.orden", Categoria.class)
                .getResultList();
    }

    @Override
    public List<Categoria> findByCreador(Usuario creador) {
        return em.createQuery(
                        "SELECT c FROM Categoria c WHERE c.creador = :creador ORDER BY c.nombre", Categoria.class)
                .setParameter("creador", creador)
                .getResultList();
    }

    @Override
    public List<Categoria> findAll(Usuario creador) {
        return em.createQuery(
                        "SELECT c FROM Categoria c WHERE c.esGlobal = true OR c.creador = :creador ORDER BY c.orden, c.nombre", Categoria.class)
                .setParameter("creador", creador)
                .getResultList();
    }

    @Override
    public void update(Categoria categoria) {
        em.merge(categoria);
    }

    @Override
    public void delete(int id) {
        Categoria categoria = findById(id);
        if (categoria != null) {
            em.remove(categoria);
        }
    }
}