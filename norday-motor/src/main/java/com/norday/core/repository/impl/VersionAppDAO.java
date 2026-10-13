package com.norday.core.repository.impl;

import com.norday.core.model.VersionApp;
import com.norday.core.repository.IVersionAppDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@Transactional(readOnly = true)
public class VersionAppDAO implements IVersionAppDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public int versionMinima(String appId) {
        VersionApp fila = em.find(VersionApp.class, appId);
        return fila == null ? 0 : fila.getVersionMinima();
    }
}
