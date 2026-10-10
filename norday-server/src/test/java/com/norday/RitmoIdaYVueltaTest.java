package com.norday;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RitmoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * El ritmo no se guarda al día: sale de los registros. Por eso marcar un día
 * pasado y deshacerlo tiene que dejarlo exactamente como estaba, sin que
 * nadie guarde ni devuelva nada, y sin tocar el ancla de la tabla `ritmo`.
 *
 * Con mocks no se puede probar —la lista de registros la pondría el propio
 * test—; hace falta la aplicación ensamblada y la base de datos.
 *
 * Semana de referencia: el lunes 5-oct-2026. "Hoy" es siempre el miércoles 7:
 * se fija en RachaService.hoyDe, que es por donde RegistroService lee el
 * reloj. Las cifras salen de una simulación aparte, no de CalculoRitmo.
 */
@SpringBootTest
@Transactional
class RitmoIdaYVueltaTest {

    private static final double D = 0.000001;
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);

    @Autowired
    private RegistroService registroService;

    @Autowired
    private RitmoService ritmoService;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private int habitoId;

    /** Un hábito creado hace meses, con su racha y su ancla en el lunes. */
    private void unHabito(Frecuencia frecuencia, int meta) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Ritmo");
        usuario.setUsername("test-ritmo-" + System.nanoTime());
        usuario.setEmail("test-ritmo-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Hábito de ida y vuelta", "Descripción", frecuencia, meta,
                usuario, null, LocalDate.of(2026, 1, 1));
        em.persist(habito);
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        habitoId = habito.getHabitoId();

        doReturn(MIERCOLES).when(rachaService).hoyDe(any(Habito.class));
    }

    /**
     * El hábito recién leído. Se vacía antes el contexto de persistencia:
     * una petición real parte de un contexto limpio, y aquí cada llamada a
     * un servicio hace de petición.
     */
    private Habito habito() {
        em.flush();
        em.clear();
        return em.find(Habito.class, habitoId);
    }

    private double ritmoDeHoy() {
        return ritmoService.ritmoVigente(habito(), MIERCOLES);
    }

    private void assertAnclaIntacta() {
        em.flush();
        em.clear();
        Ritmo ancla = em.createQuery(
                        "SELECT r FROM Ritmo r WHERE r.habito.habitoId = :habitoId", Ritmo.class)
                .setParameter("habitoId", habitoId)
                .getSingleResult();
        assertEquals(0.5, ancla.getValor(), 0.0, "valor del ancla");
        assertEquals(LUNES, ancla.getDesde(), "fecha del ancla");
    }

    @Test
    void diario_marcarUnDiaPasadoCambiaElRitmo_yDeshacerloLoDejaComoEstaba() {
        unHabito(Frecuencia.DIARIO, 1);

        double antes = ritmoDeHoy();
        Map<String, Object> completado = registroService.completarHabito(habito(), "", MARTES);
        double conElMartes = ritmoDeHoy();
        registroService.deshacerRegistro((Integer) completado.get("registroId"));
        double despues = ritmoDeHoy();

        // Lunes y martes sin nada; con el martes hecho; y otra vez sin nada.
        assertEquals(0.468059, antes, D);
        assertEquals(0.532435, conElMartes, D);
        assertEquals(antes, despues, 0.0);
        assertAnclaIntacta();
    }

    @Test
    void diario_marcarHoyNoMueveElRitmo() {
        unHabito(Frecuencia.DIARIO, 1);

        double antes = ritmoDeHoy();
        registroService.completarHabito(habito(), "", null);
        double despues = ritmoDeHoy();

        // El día de hoy sigue abierto: no cuenta hasta que termine.
        assertEquals(0.468059, antes, D);
        assertEquals(antes, despues, 0.0);
        assertAnclaIntacta();
    }

    @Test
    void semanal_marcarDentroDeLaSemanaNoMueveElRitmo_niAlDeshacer() {
        unHabito(Frecuencia.SEMANAL, 3);

        double antes = ritmoDeHoy();
        Map<String, Object> completado = registroService.completarHabito(habito(), "", MARTES);
        double conElMartes = ritmoDeHoy();
        registroService.deshacerRegistro((Integer) completado.get("registroId"));
        double despues = ritmoDeHoy();

        // La semana sigue abierta: el ritmo no se mueve hasta que cierre.
        assertEquals(0.5, antes, 0.0);
        assertEquals(0.5, conElMartes, 0.0);
        assertEquals(0.5, despues, 0.0);
        assertAnclaIntacta();
    }
}
