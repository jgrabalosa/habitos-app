package com.norday.habitos.repository.impl;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Habito;
import com.norday.habitos.repository.IHabitoDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Repository
@Transactional
public class HabitoDAO implements IHabitoDAO {

    @PersistenceContext
    private EntityManager em;

    @Override
    public void save(Habito habito) {
        em.persist(habito);
    }

    @Override
    public Habito findById(int id) {
        return em.find(Habito.class, id);
    }

    @Override
    public List<Habito> findByPropietario(Usuario propietario) {
        return em.createQuery(
                        "SELECT h FROM Habito h WHERE h.propietario = :propietario ORDER BY h.nombre", Habito.class)
                .setParameter("propietario", propietario)
                .getResultList();
    }

    @Override
    public List<Habito> findActivos(Usuario propietario) {
        return em.createQuery(
                        "SELECT h FROM Habito h WHERE h.propietario = :propietario AND h.activo = true ORDER BY h.nombre", Habito.class)
                .setParameter("propietario", propietario)
                .getResultList();
    }

    @Override
    public void update(Habito habito) {
        em.merge(habito);
    }

    @Override
    public void delete(int id) {
        Habito habito = findById(id);
        if (habito != null) {
            em.remove(habito);
        }
    }

    @Override
    public List<Habito> findTodosActivos() {
        // JOIN FETCH del propietario a propósito: los dos schedulers que usan
        // este método (NotificacionScheduler, NotificadorRachaEnPeligro) leen
        // habito.getPropietario() fuera de la sesión de Hibernate. Con el
        // propietario en LAZY y sin este fetch, lanzan
        // LazyInitializationException y abortan el envío de notificaciones.
        return em.createQuery(
                        "SELECT h FROM Habito h JOIN FETCH h.propietario WHERE h.activo = true", Habito.class)
                .getResultList();
    }

    // Un UPDATE de las columnas del objetivo y no un merge del hábito: el
    // merge (o el flush de una entidad de la sesión) escribe todas las
    // columnas con lo que hay en memoria, y una lectura que leyó el hábito
    // antes de una pausa o de un cambio de nombre lo devolvía activo y con
    // el nombre viejo. Después se relee: así tampoco queda en memoria nada
    // viejo que un flush posterior pudiera escribir. Uno que no está en la
    // sesión no se puede releer; se le pone el objetivo en memoria.
    @Override
    public void actualizarObjetivo(Habito habito, com.norday.habitos.model.Objetivo objetivo) {
        em.createQuery("UPDATE Habito h SET h.unidad = :unidad, h.minutosObjetivo = :minutos, "
                        + "h.frecuencia = :frecuencia, h.meta = :meta, h.diasSemana = :dias "
                        + "WHERE h.habitoId = :id")
                .setParameter("unidad", objetivo.unidad())
                .setParameter("minutos", objetivo.minutosObjetivo())
                .setParameter("frecuencia", objetivo.frecuencia())
                .setParameter("meta", objetivo.meta())
                .setParameter("dias", objetivo.diasSemana())
                .setParameter("id", habito.getHabitoId())
                .executeUpdate();
        if (em.contains(habito)) {
            em.refresh(habito);
        } else {
            habito.setUnidad(objetivo.unidad());
            habito.setMinutosObjetivo(objetivo.minutosObjetivo());
            habito.setFrecuencia(objetivo.frecuencia());
            habito.setMeta(objetivo.meta());
            habito.setDiasSemana(objetivo.diasSemana());
        }
    }

    // PESSIMISTIC_WRITE es SELECT … FOR UPDATE en Postgres: otra transacción
    // que quiera el mismo bloqueo espera a que esta termine.
    //
    // refresh y no find con bloqueo: si el hábito ya estaba en la sesión (el
    // controlador lo lee antes, con open-in-view), find solo pide el bloqueo
    // y deja los valores que se leyeron antes de esperar, viejos si otro los
    // ha cambiado entretanto (una pausa, un objetivo nuevo). refresh los
    // vuelve a leer en la misma consulta que bloquea.
    @Override
    public Habito bloquear(int habitoId) {
        Habito habito = em.find(Habito.class, habitoId);
        if (habito == null) return null;
        try {
            em.refresh(habito, jakarta.persistence.LockModeType.PESSIMISTIC_WRITE);
        } catch (jakarta.persistence.EntityNotFoundException e) {
            // Estaba en la sesión, pero otro lo ha borrado mientras esperaba.
            em.detach(habito);
            return null;
        }
        return habito;
    }
}
