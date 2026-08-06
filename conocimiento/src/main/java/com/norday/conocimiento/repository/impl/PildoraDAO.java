package com.norday.conocimiento.repository.impl;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.PildoraCategoria;
import com.norday.conocimiento.repository.IPildoraDAO;
import com.norday.core.model.Usuario;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class PildoraDAO implements IPildoraDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public Pildora findById(int id) {
        return em.find(Pildora.class, id);
    }

    /**
     * El NOT EXISTS solo descarta lo ya leído (VISTA o GUARDADA). Lo descartado
     * sigue en el resultado: filtrarlo aquí obligaría a meter en SQL el conteo
     * de interacciones posteriores, y esa regla se lee mucho mejor en Java.
     */
    @Override
    public List<Pildora> findActivasElegibles(Usuario usuario, int categoriaId) {
        return em.createQuery(
                        "SELECT p FROM Pildora p " +
                        "WHERE p.activa = true " +
                        "AND EXISTS (SELECT 1 FROM PildoraCategoria pc " +
                        "            WHERE pc.pildora = p AND pc.categoria.categoriaId = :categoriaId) " +
                        "AND NOT EXISTS (SELECT 1 FROM UsuarioPildora up " +
                        "                WHERE up.pildora = p AND up.usuario = :usuario " +
                        "                AND up.estado IN (com.norday.conocimiento.model.EstadoPildora.VISTA, " +
                        "                                  com.norday.conocimiento.model.EstadoPildora.GUARDADA))",
                        Pildora.class)
                .setParameter("categoriaId", categoriaId)
                .setParameter("usuario", usuario)
                .getResultList();
    }

    @Override
    public PildoraCategoria findCategoriaPrincipal(int pildoraId) {
        return em.createQuery(
                        "SELECT pc FROM PildoraCategoria pc " +
                        "WHERE pc.pildora.pildoraId = :pildoraId AND pc.esPrincipal = true",
                        PildoraCategoria.class)
                .setParameter("pildoraId", pildoraId)
                .getResultStream().findFirst().orElse(null);
    }

    @Override
    public List<Categoria> findCategorias(int pildoraId) {
        return em.createQuery(
                        "SELECT pc.categoria FROM PildoraCategoria pc " +
                        "WHERE pc.pildora.pildoraId = :pildoraId " +
                        "ORDER BY pc.esPrincipal DESC, pc.categoria.orden",
                        Categoria.class)
                .setParameter("pildoraId", pildoraId)
                .getResultList();
    }
}
