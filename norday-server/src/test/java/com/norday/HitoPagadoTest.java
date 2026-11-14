package com.norday;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HitoPagado;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * El libro de hitos con la aplicación ensamblada: la fila se escribe en el
 * mismo completado, deshacerlo la borra (con la FK de verdad) y un hito ya
 * cobrado no se vuelve a pagar aunque el contador baje al subir la meta.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe como en
 * PremioPorPasoTest. La semana en curso empieza el lunes 5.
 */
@SpringBootTest
@Transactional
class HitoPagadoTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Autowired
    private RegistroService registroService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private int usuarioId;
    private int habitoId;

    private void unHabitoDiario(int meta) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Hito");
        usuario.setUsername("test-hito-" + System.nanoTime());
        usuario.setEmail("test-hito-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Hábito de hitos", "Descripción", Frecuencia.DIARIO, meta,
                usuario, null, LocalDate.of(2026, 1, 1));
        em.persist(habito);
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        usuarioId = usuario.getUsuarioId();
        habitoId = habito.getHabitoId();

        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    private Habito habito() {
        em.flush();
        em.clear();
        return em.find(Habito.class, habitoId);
    }

    private int saldo() {
        em.flush();
        return usuarioMonedaService.consultarSaldo(usuarioId);
    }

    private List<HitoPagado> libro() {
        em.flush();
        em.clear();
        return em.createQuery(
                        "SELECT h FROM HitoPagado h LEFT JOIN FETCH h.registro "
                                + "WHERE h.habito.habitoId = :habitoId ORDER BY h.umbral",
                        HitoPagado.class)
                .setParameter("habitoId", habitoId)
                .getResultList();
    }

    private int puntosDelLibro(String origen) {
        em.flush();
        Long suma = em.createQuery(
                        "SELECT COALESCE(SUM(m.cantidad), 0) FROM UsuarioMoneda m "
                                + "WHERE m.usuario.usuarioId = :usuarioId AND m.origen = :origen",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("origen", origen)
                .getSingleResult();
        return suma.intValue();
    }

    @Test
    void deshacerElCompletadoQuePagoElHito_borraLaFilaYDevuelveLosPuntos() {
        unHabitoDiario(1);
        registroService.completarHabito(habito(), "", LUNES);
        registroService.completarHabito(habito(), "", LUNES.plusDays(1));
        assertTrue(libro().isEmpty());
        int saldoAntesDelTercero = saldo();

        Map<String, Object> tercero =
                registroService.completarHabito(habito(), "", LUNES.plusDays(2));

        assertEquals(50, tercero.get("puntosHito"));
        List<HitoPagado> libro = libro();
        assertEquals(1, libro.size());
        assertEquals(3, libro.get(0).getUmbral());
        assertEquals(tercero.get("registroId"), libro.get(0).getRegistro().getRegistroId());
        assertNotNull(libro.get(0).getPagadoEn());

        registroService.deshacerRegistro((Integer) tercero.get("registroId"));

        assertTrue(libro().isEmpty());
        assertEquals(saldoAntesDelTercero, saldo());

        // Deshecho, el hito vuelve a estar por cobrar.
        Map<String, Object> otraVez =
                registroService.completarHabito(habito(), "", LUNES.plusDays(2));
        assertEquals(50, otraVez.get("puntosHito"));
        assertEquals(1, libro().size());
    }

    @Test
    void subirLaMetaYVolverACruzarElUmbral_noPagaElHitoOtraVez() {
        // La semana anterior, dos días con dos registros cada uno. Con meta 1
        // son dos días hechos, y el lunes, el tercero: cobra el hito de 3.
        unHabitoDiario(1);
        Habito h = habito();
        for (LocalDate dia : List.of(LUNES.minusDays(7), LUNES.minusDays(6))) {
            em.persist(new Registro(h, true, null, dia));
            em.persist(new Registro(h, true, null, dia));
        }
        Map<String, Object> lunes = registroService.completarHabito(habito(), "", LUNES);
        assertEquals(50, lunes.get("puntosHito"));
        assertEquals(50, puntosDelLibro("HITO_DIAS_HECHOS"));

        // Sube la meta a 2: el lunes deja de contar y el contador baja a 2.
        Habito conMetaNueva = habito();
        conMetaNueva.setMeta(2);
        em.flush();

        // El martes, dos veces: el segundo vuelve a cruzar el 3.
        registroService.completarHabito(habito(), "", LUNES.plusDays(1));
        Map<String, Object> segundo = registroService.completarHabito(habito(), "", LUNES.plusDays(1));

        assertEquals(0, segundo.get("puntosHito"));
        assertEquals(50, puntosDelLibro("HITO_DIAS_HECHOS"));
        assertEquals(1, libro().size());
    }
}
