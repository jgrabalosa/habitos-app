package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.core.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class UsuarioCategoriaPreferenciaDAO implements IUsuarioCategoriaPreferenciaDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public List<UsuarioCategoriaPreferencia> findByUsuario(Usuario usuario) {
        return em.createQuery(
                        "SELECT p FROM UsuarioCategoriaPreferencia p " +
                        "WHERE p.usuario = :usuario ORDER BY p.categoria.orden",
                        UsuarioCategoriaPreferencia.class)
                .setParameter("usuario", usuario)
                .getResultList();
    }

    @Override
    public UsuarioCategoriaPreferencia findByUsuarioYCategoria(Usuario usuario, int categoriaId) {
        return em.createQuery(
                        "SELECT p FROM UsuarioCategoriaPreferencia p " +
                        "WHERE p.usuario = :usuario AND p.categoria.categoriaId = :categoriaId",
                        UsuarioCategoriaPreferencia.class)
                .setParameter("usuario", usuario)
                .setParameter("categoriaId", categoriaId)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    public void save(UsuarioCategoriaPreferencia preferencia) {
        em.persist(preferencia);
    }

    @Override
    public void update(UsuarioCategoriaPreferencia preferencia) {
        em.merge(preferencia);
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioCategoriaPreferencia p WHERE p.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}
