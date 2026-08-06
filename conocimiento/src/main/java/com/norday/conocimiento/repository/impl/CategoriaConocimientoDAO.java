package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.repository.ICategoriaConocimientoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class CategoriaConocimientoDAO implements ICategoriaConocimientoDAO {

    @PersistenceContext
    private EntityManager em;

    // Ojo con el JPQL de este módulo: la entidad se llama CategoriaConocimiento,
    // no Categoria — el nombre simple ya lo ocupa el módulo de hábitos.

    @Override
    public List<Categoria> findAll() {
        return em.createQuery(
                        "SELECT c FROM CategoriaConocimiento c ORDER BY c.orden", Categoria.class)
                .getResultList();
    }

    @Override
    public Categoria findById(int id) {
        return em.find(Categoria.class, id);
    }
}
