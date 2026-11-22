package com.norday.habitos.repository.impl;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.repository.ICategoriaDAO;
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
    public Categoria findByCodigo(String codigo) {
        return em.createQuery(
                        "SELECT c FROM Categoria c WHERE c.codigo = :codigo", Categoria.class)
                .setParameter("codigo", codigo)
                .getResultStream().findFirst().orElse(null);
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
    public long contarPropias(Usuario creador) {
        return em.createQuery(
                        "SELECT COUNT(c) FROM Categoria c WHERE c.creador = :creador AND c.esGlobal = false", Long.class)
                .setParameter("creador", creador)
                .getSingleResult();
    }

    @Override
    public boolean existeNombre(Usuario creador, String nombre, int excluirId) {
        Long coincidencias = em.createQuery(
                        "SELECT COUNT(c) FROM Categoria c WHERE (c.esGlobal = true OR c.creador = :creador) "
                                + "AND lower(trim(c.nombre)) = :nombre AND c.categoriaId <> :excluirId", Long.class)
                .setParameter("creador", creador)
                .setParameter("nombre", nombre.trim().toLowerCase(java.util.Locale.ROOT))
                .setParameter("excluirId", excluirId)
                .getSingleResult();
        return coincidencias > 0;
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