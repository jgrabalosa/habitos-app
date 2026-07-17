package com.joaquim.habitosapp.repository.impl;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioLogro;
import com.joaquim.habitosapp.repository.IUsuarioLogroDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class UsuarioLogroDAO implements IUsuarioLogroDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(UsuarioLogro usuarioLogro) {
        em.persist(usuarioLogro);
    }

    @Override
    public UsuarioLogro findById(int id) {
        return em.find(UsuarioLogro.class, id);
    }

    @Override
    public List<UsuarioLogro> findByUsuario(Usuario usuario) {
        return em.createQuery(
                        "SELECT ul FROM UsuarioLogro ul WHERE ul.usuario = :usuario ORDER BY ul.fechaConseguido DESC",
                        UsuarioLogro.class)
                .setParameter("usuario", usuario)
                .getResultList();
    }

    @Override
    public boolean existePorUsuarioYLogro(int usuarioId, int logroId) {
        Long count = em.createQuery(
                        "SELECT COUNT(ul) FROM UsuarioLogro ul WHERE ul.usuario.usuarioId = :usuarioId AND ul.logro.logroId = :logroId",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("logroId", logroId)
                .getSingleResult();
        return count > 0;
    }
    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM UsuarioLogro ul WHERE ul.usuario.usuarioId = :usuarioId")
                .setParameter("usuarioId", usuarioId)
                .executeUpdate();
    }
}