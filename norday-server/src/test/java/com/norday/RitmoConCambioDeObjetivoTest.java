package com.norday;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RachaService;
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
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * El ritmo de hace siete días se juzga con el objetivo que tenía cada día,
 * no con el de hoy. Tras un cambio de objetivo, los días de antes no se
 * vuelven a juzgar con la meta nueva: la tendencia no cambia sin motivo.
 *
 * Dos hábitos gemelos, diarios de veces con meta 3 y hechos 1 de 3 cada día
 * durante seis semanas. El viernes uno pasa a minutos (30 al día) y el otro
 * no cambia. El ritmo de hace siete días tiene que ser el mismo en los dos:
 * hace siete días ninguno había cambiado.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe.
 */
@SpringBootTest
@Transactional
class RitmoConCambioDeObjetivoTest {

    private static final double D = 1e-9;
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate INICIO = VIERNES.minusWeeks(6);

    @Autowired private RitmoService ritmoService;
    @Autowired private HabitoService habitoService;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;

    private void unUsuario() {
        usuario = new Usuario();
        usuario.setNombre("Test Ritmo Cambio");
        usuario.setUsername("test-ritcam-" + System.nanoTime());
        usuario.setEmail("test-ritcam-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    /** Diario de veces, meta 3, creado hace seis semanas y hecho 1 de 3 cada día hasta ayer. */
    private int unDiarioDe3HechoUnaVezAlDia(String nombre) {
        Habito habito = new Habito(nombre, null, Frecuencia.DIARIO, 3, usuario, null, INICIO);
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, new Objetivo(Frecuencia.DIARIO, 3, null)));
        em.persist(new Racha(habito, INICIO));
        em.persist(new Ritmo(habito, INICIO));
        for (LocalDate dia = INICIO; dia.isBefore(VIERNES); dia = dia.plusDays(1)) {
            em.persist(new Registro(habito, true, null, dia));
        }
        em.flush();
        return habito.getHabitoId();
    }

    private Habito leido(int id) {
        em.flush();
        em.clear();
        return em.find(Habito.class, id);
    }

    @Test
    void trasPasarAMinutos_elRitmoDeHaceSieteDiasEsElDelGemeloSinCambio() {
        unUsuario();
        int conCambio = unDiarioDe3HechoUnaVezAlDia("Con cambio");
        int sinCambio = unDiarioDe3HechoUnaVezAlDia("Sin cambio");

        // El viernes, a minutos: 30 al día (el formulario, como un PUT).
        Habito existente = leido(conCambio);
        Habito editado = new Habito("Con cambio", null, Frecuencia.DIARIO, 1, existente.getPropietario(), null, null);
        editado.setHabitoId(conCambio);
        assertEquals(VIERNES, habitoService.actualizar(editado, null, Unidad.MINUTOS, 30));

        RitmoLeido cambiado = ritmoService.ritmoLeido(leido(conCambio), VIERNES);
        RitmoLeido gemelo = ritmoService.ritmoLeido(leido(sinCambio), VIERNES);
        assertEquals(gemelo.haceUnaSemana(), cambiado.haceUnaSemana(), D, "hace siete días ninguno había cambiado");
        assertEquals(gemelo.valor(), cambiado.valor(), D, "y hoy se conserva el ritmo que había");

        // Leído en grupo (la pantalla de Hoy), lo mismo.
        Map<Integer, RitmoLeido> enGrupo = ritmoService.ritmosLeidos(List.of(leido(conCambio), leido(sinCambio)),
                VIERNES);
        assertEquals(enGrupo.get(sinCambio).haceUnaSemana(), enGrupo.get(conCambio).haceUnaSemana(), D);
    }

    @Test
    void trasBajarLaMetaDe3A1_tampocoCambiaElDeHaceSieteDias() {
        unUsuario();
        int conCambio = unDiarioDe3HechoUnaVezAlDia("Con cambio");
        int sinCambio = unDiarioDe3HechoUnaVezAlDia("Sin cambio");

        Habito existente = leido(conCambio);
        Habito editado = new Habito("Con cambio", null, Frecuencia.DIARIO, 1, existente.getPropietario(), null, null);
        editado.setHabitoId(conCambio);
        habitoService.actualizar(editado, null, Unidad.VECES, null);

        RitmoLeido cambiado = ritmoService.ritmoLeido(leido(conCambio), VIERNES);
        RitmoLeido gemelo = ritmoService.ritmoLeido(leido(sinCambio), VIERNES);
        assertEquals(gemelo.haceUnaSemana(), cambiado.haceUnaSemana(), D, "hace siete días ninguno había cambiado");
    }
}
