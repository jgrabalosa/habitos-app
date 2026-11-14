package com.norday.habitos.repository.impl;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HitoPagado;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHitoPagadoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

@Repository
@Transactional
public class HitoPagadoDAO implements IHitoPagadoDAO {

    @PersistenceContext
    private EntityManager em;

    // Consulta nativa con ON CONFLICT DO NOTHING: si dos completados
    // simultáneos cruzan el mismo umbral, el segundo espera al primero y no
    // escribe nada, en vez de chocar con la restricción única y perder el
    // completado entero. Quien llama paga solo si se ha escrito la fila.
    @Override
    public boolean apuntar(Habito habito, int umbral, Registro registro) {
        int filas = em.createNativeQuery(
                        "INSERT INTO hito_pagado (habito_ref, umbral, registro_ref, pagado_en) "
                                + "VALUES (:habito, :umbral, :registro, :pagadoEn) "
                                + "ON CONFLICT (habito_ref, umbral) DO NOTHING")
                .setParameter("habito", habito.getHabitoId())
                .setParameter("umbral", umbral)
                .setParameter("registro", registro.getRegistroId())
                .setParameter("pagadoEn", LocalDateTime.now(ZoneOffset.UTC))
                .executeUpdate();
        return filas == 1;
    }

    @Override
    public List<Integer> findUmbralesByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT h.umbral FROM HitoPagado h WHERE h.habito = :habito", Integer.class)
                .setParameter("habito", habito)
                .getResultList();
    }

    // Uno a uno con remove, no con un DELETE en bloque: al deshacer, el
    // registro se borra después con em.remove, y un DELETE de JPQL no saca
    // del contexto de persistencia las filas que ya estuvieran cargadas, que
    // seguirían apuntando a un registro borrado al hacer flush.
    @Override
    public void deleteByRegistro(int registroId) {
        em.createQuery(
                        "SELECT h FROM HitoPagado h WHERE h.registro.registroId = :registroId",
                        HitoPagado.class)
                .setParameter("registroId", registroId)
                .getResultList()
                .forEach(em::remove);
    }

    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery("DELETE FROM HitoPagado h WHERE h.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
}
