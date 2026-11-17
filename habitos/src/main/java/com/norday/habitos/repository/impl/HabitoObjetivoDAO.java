package com.norday.habitos.repository.impl;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Repository
@Transactional
public class HabitoObjetivoDAO implements IHabitoObjetivoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(HabitoObjetivo version) {
        em.persist(version);
    }

    // Nativa con ON CONFLICT DO NOTHING, como HitoPagadoDAO: si dos PUT del
    // mismo hábito crean a la vez la misma (hábito, desde), el segundo espera
    // al primero y no escribe nada, en vez de chocar con la restricción única
    // y responder un 500. creado_en va en UTC, como en HabitoObjetivo.alCrear.
    @Override
    public boolean insertarSiLibre(HabitoObjetivo version) {
        int filas = em.createNativeQuery(
                        "INSERT INTO habito_objetivo (habito_ref, desde, unidad, frecuencia, meta,"
                                + " minutos_objetivo, dias_semana, creado_en)"
                                + " VALUES (:habito, :desde, :unidad, :frecuencia, :meta,"
                                + " CAST(:minutos AS INTEGER), CAST(:dias AS VARCHAR(20)), :creadoEn)"
                                + " ON CONFLICT (habito_ref, desde) DO NOTHING")
                .setParameter("habito", version.getHabito().getHabitoId())
                .setParameter("desde", version.getDesde())
                .setParameter("unidad", version.getUnidad().name())
                .setParameter("frecuencia", version.getFrecuencia().name())
                .setParameter("meta", version.getMeta())
                .setParameter("minutos", version.getMinutosObjetivo() == null
                        ? null : String.valueOf(version.getMinutosObjetivo()))
                .setParameter("dias", version.getDiasSemana())
                .setParameter("creadoEn", java.time.LocalDateTime.now(java.time.ZoneOffset.UTC))
                .executeUpdate();
        return filas == 1;
    }

    // Una a una con remove, por el mismo motivo que delete.
    @Override
    public void deletePosterioresA(Habito habito, java.time.LocalDate fecha) {
        em.createQuery("SELECT o FROM HabitoObjetivo o WHERE o.habito = :habito AND o.desde > :fecha",
                        HabitoObjetivo.class)
                .setParameter("habito", habito)
                .setParameter("fecha", fecha)
                .getResultList()
                .forEach(em::remove);
    }

    @Override
    public void update(HabitoObjetivo version) {
        em.merge(version);
    }

    // Con remove y no con un DELETE de JPQL: quien borra una versión
    // programada puede tener cargadas las demás, y un DELETE en bloque no
    // las saca del contexto de persistencia.
    @Override
    public void delete(int id) {
        HabitoObjetivo version = em.find(HabitoObjetivo.class, id);
        if (version != null) {
            em.remove(version);
        }
    }

    @Override
    public List<HabitoObjetivo> findByHabito(Habito habito) {
        return em.createQuery(
                        "SELECT o FROM HabitoObjetivo o WHERE o.habito = :habito ORDER BY o.desde",
                        HabitoObjetivo.class)
                .setParameter("habito", habito)
                .getResultList();
    }

    // El id del hábito sale en la propia consulta: la asociación es LAZY y
    // leerlo del proxy fuera de una transacción podría reventar.
    @Override
    public Map<Integer, List<HabitoObjetivo>> findByHabitos(List<Habito> habitos) {
        Map<Integer, List<HabitoObjetivo>> porHabito = new HashMap<>();
        if (habitos.isEmpty()) {
            return porHabito;
        }
        List<Object[]> filas = em.createQuery(
                        "SELECT o.habito.habitoId, o FROM HabitoObjetivo o "
                                + "WHERE o.habito IN :habitos ORDER BY o.desde", Object[].class)
                .setParameter("habitos", habitos)
                .getResultList();
        for (Object[] fila : filas) {
            porHabito.computeIfAbsent((Integer) fila[0], id -> new ArrayList<>())
                    .add((HabitoObjetivo) fila[1]);
        }
        return porHabito;
    }

    @Override
    public void deleteByHabito(int habitoId) {
        em.createQuery("DELETE FROM HabitoObjetivo o WHERE o.habito.habitoId = :habitoId")
                .setParameter("habitoId", habitoId)
                .executeUpdate();
    }
}
