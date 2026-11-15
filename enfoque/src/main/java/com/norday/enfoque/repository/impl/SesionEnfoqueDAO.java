package com.norday.enfoque.repository.impl;

import com.norday.enfoque.model.EstadoAporte;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@Transactional
public class SesionEnfoqueDAO implements ISesionEnfoqueDAO {

    private static final List<EstadoSesion> QUE_CUENTAN = List.of(EstadoSesion.COMPLETADA, EstadoSesion.TERMINADA);

    @PersistenceContext
    private EntityManager em;

    @Override
    public SesionEnfoque findByClave(UUID clave) {
        return em.createQuery("SELECT s FROM SesionEnfoque s WHERE s.clave = :clave", SesionEnfoque.class)
                .setParameter("clave", clave)
                .getResultStream()
                .findFirst()
                .orElse(null);
    }

    @Override
    public SesionEnfoque findById(int id) {
        return em.find(SesionEnfoque.class, id);
    }

    @Override
    public SesionEnfoque releer(int id) {
        SesionEnfoque sesion = em.find(SesionEnfoque.class, id);
        if (sesion != null) {
            em.refresh(sesion);
        }
        return sesion;
    }

    @Override
    public Integer insertarSiClaveLibre(SesionEnfoque sesion) {
        List<?> ids = em.createNativeQuery(
                        "INSERT INTO sesion_enfoque (usuario_ref, clave, inicio_utc, duracion_seg,"
                                + " duracion_objetivo_min, estado, dia, destino_tipo, destino_ref,"
                                + " aporte_estado, aporte_motivo, minutos_aportados, monedas_aportadas,"
                                + " xp_aportada, logros_aportados, creado_en)"
                                + " VALUES (:usuario, CAST(:clave AS UUID), :inicio, :duracion, :objetivo,"
                                + " :estado, :dia, CAST(:destinoTipo AS VARCHAR(20)), CAST(:destinoRef AS BIGINT),"
                                + " :aporteEstado, CAST(:aporteMotivo AS VARCHAR(40)), :minutosAportados,"
                                + " :monedas, :xp, CAST(:logros AS VARCHAR(1000)), :creadoEn)"
                                + " ON CONFLICT (clave) DO NOTHING"
                                + " RETURNING id")
                .setParameter("usuario", sesion.getUsuario().getUsuarioId())
                .setParameter("clave", sesion.getClave().toString())
                .setParameter("inicio", sesion.getInicioUtc())
                .setParameter("duracion", sesion.getDuracionSeg())
                .setParameter("objetivo", sesion.getDuracionObjetivoMin())
                .setParameter("estado", sesion.getEstado().name())
                .setParameter("dia", Date.valueOf(sesion.getDia()))
                .setParameter("destinoTipo", sesion.getDestinoTipo())
                .setParameter("destinoRef", sesion.getDestinoRef())
                .setParameter("aporteEstado", sesion.getAporteEstado().name())
                .setParameter("aporteMotivo", sesion.getAporteMotivo())
                .setParameter("minutosAportados", sesion.getMinutosAportados())
                .setParameter("monedas", sesion.getMonedasAportadas())
                .setParameter("xp", sesion.getXpAportada())
                .setParameter("logros", sesion.getLogrosAportados())
                .setParameter("creadoEn", sesion.getCreadoEn())
                .getResultList();
        return ids.isEmpty() ? null : ((Number) ids.get(0)).intValue();
    }

    @Override
    public boolean haySolape(int usuarioId, LocalDateTime inicio, LocalDateTime fin) {
        List<?> filas = em.createNativeQuery(
                        "SELECT 1 FROM sesion_enfoque WHERE usuario_ref = :usuario"
                                + " AND inicio_utc < :fin"
                                + " AND :inicio < inicio_utc + make_interval(secs => duracion_seg)"
                                + " LIMIT 1")
                .setParameter("usuario", usuarioId)
                .setParameter("inicio", inicio)
                .setParameter("fin", fin)
                .getResultList();
        return !filas.isEmpty();
    }

    // El orden de precedencia, en el propio UPDATE: comprobarlo antes en Java
    // dejaría un hueco entre leer y escribir. SIN_DESTINO no entra (99): una
    // sesión sin destino no recibe resultados.
    @Override
    public boolean guardarAporte(int sesionId, EstadoAporte estado, String motivo, int minutosAportados,
                                 int monedas, int xp, String logros) {
        int filas = em.createNativeQuery(
                        "UPDATE sesion_enfoque SET aporte_estado = :estado,"
                                + " aporte_motivo = CAST(:motivo AS VARCHAR(40)), minutos_aportados = :minutos,"
                                + " monedas_aportadas = :monedas, xp_aportada = :xp,"
                                + " logros_aportados = CAST(:logros AS VARCHAR(1000))"
                                + " WHERE id = :id AND (CASE aporte_estado WHEN 'PENDIENTE' THEN 0"
                                + " WHEN 'RECHAZADO' THEN 1 WHEN 'REPETIDO' THEN 2 WHEN 'ACEPTADO' THEN 3"
                                + " ELSE 99 END) < :rango")
                .setParameter("estado", estado.name())
                .setParameter("motivo", motivo)
                .setParameter("minutos", minutosAportados)
                .setParameter("monedas", monedas)
                .setParameter("xp", xp)
                .setParameter("logros", logros)
                .setParameter("id", sesionId)
                .setParameter("rango", rango(estado))
                .executeUpdate();
        return filas > 0;
    }

    /** El rango de un resultado: gana a los que tienen un rango menor. */
    private static int rango(EstadoAporte estado) {
        return switch (estado) {
            case RECHAZADO -> 1;
            case REPETIDO -> 2;
            case ACEPTADO -> 3;
            case PENDIENTE, SIN_DESTINO -> throw new IllegalArgumentException("No es un resultado: " + estado);
        };
    }

    @Override
    public List<SesionEnfoque> findByUsuarioEntre(int usuarioId, LocalDate desde, LocalDate hasta) {
        return em.createQuery(
                        "SELECT s FROM SesionEnfoque s WHERE s.usuario.usuarioId = :usuario"
                                + " AND s.dia BETWEEN :desde AND :hasta ORDER BY s.inicioUtc", SesionEnfoque.class)
                .setParameter("usuario", usuarioId)
                .setParameter("desde", desde)
                .setParameter("hasta", hasta)
                .getResultList();
    }

    @Override
    public List<LocalDate> diasQueCuentanHasta(int usuarioId, LocalDate hasta) {
        return em.createQuery(
                        "SELECT DISTINCT s.dia FROM SesionEnfoque s WHERE s.usuario.usuarioId = :usuario"
                                + " AND s.estado IN :estados AND s.dia <= :hasta ORDER BY s.dia DESC", LocalDate.class)
                .setParameter("usuario", usuarioId)
                .setParameter("estados", QUE_CUENTAN)
                .setParameter("hasta", hasta)
                .getResultList();
    }

    @Override
    public List<SesionEnfoque> pagina(int usuarioId, int pagina, int tamano) {
        return em.createQuery(
                        "SELECT s FROM SesionEnfoque s WHERE s.usuario.usuarioId = :usuario"
                                + " ORDER BY s.inicioUtc DESC, s.id DESC", SesionEnfoque.class)
                .setParameter("usuario", usuarioId)
                // En long y con toIntExact: si algún día crecen los límites,
                // un desbordamiento lanza en vez de servir otra página.
                .setFirstResult(Math.toIntExact((long) pagina * tamano))
                .setMaxResults(tamano)
                .getResultList();
    }

    @Override
    public long contar(int usuarioId) {
        return em.createQuery("SELECT COUNT(s) FROM SesionEnfoque s WHERE s.usuario.usuarioId = :usuario", Long.class)
                .setParameter("usuario", usuarioId)
                .getSingleResult();
    }

    @Override
    public List<SesionEnfoque> findByUsuario(int usuarioId) {
        return em.createQuery(
                        "SELECT s FROM SesionEnfoque s WHERE s.usuario.usuarioId = :usuario"
                                + " ORDER BY s.inicioUtc, s.id", SesionEnfoque.class)
                .setParameter("usuario", usuarioId)
                .getResultList();
    }

    @Override
    public void deleteByUsuario(int usuarioId) {
        em.createQuery("DELETE FROM SesionEnfoque s WHERE s.usuario.usuarioId = :usuario")
                .setParameter("usuario", usuarioId)
                .executeUpdate();
    }
}
