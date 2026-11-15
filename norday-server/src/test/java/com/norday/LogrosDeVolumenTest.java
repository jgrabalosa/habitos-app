package com.norday;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
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
 * Los logros de volumen (PRIMEROS_PASOS, REGISTROS_100…) se conceden al
 * alcanzar o superar su número, no solo al caer justo en él. Deshacer retira
 * el logro que dio un completado, y para entonces el contador puede haber
 * pasado de largo: si la regla fuera de igualdad, ya no volvería nunca.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe.
 */
@SpringBootTest
@Transactional
class LogrosDeVolumenTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Autowired private RegistroService registroService;
    @Autowired private IUsuarioLogroDAO usuarioLogroDAO;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;

    private Habito habitoDeMinutos(String nombre) {
        if (usuario == null) {
            usuario = new Usuario();
            usuario.setNombre("Test Volumen");
            usuario.setUsername("test-vol-" + System.nanoTime());
            usuario.setEmail("test-vol-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
        }
        Objetivo objetivo = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null);
        Habito habito = new Habito(nombre, null, Frecuencia.DIARIO, 1, usuario, null, INICIO);
        habito.setUnidad(Unidad.MINUTOS);
        habito.setMinutosObjetivo(30);
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, objetivo));
        em.persist(new Racha(habito, VIERNES));
        em.persist(new Ritmo(habito, VIERNES));
        em.flush();
        return habito;
    }

    private Map<String, Object> paso(Habito habito, String minutos) {
        em.flush();
        em.clear();
        return registroService.completar(em.find(Habito.class, habito.getHabitoId()),
                new Solicitud(null, VIERNES, minutos, null, OrigenRegistro.MANUAL));
    }

    private boolean tiene(String codigo) {
        em.flush();
        List<UsuarioLogro> logros = usuarioLogroDAO.findByUsuario(em.find(Usuario.class, usuario.getUsuarioId()));
        return logros.stream().anyMatch(ul -> codigo.equals(ul.getLogro().getCodigo()));
    }

    @Test
    void primerosPasos_retiradoAlDeshacer_vuelveAunqueElContadorHayaPasadoDeUno() {
        Habito a = habitoDeMinutos("A");
        Habito b = habitoDeMinutos("B");

        Map<String, Object> pasoA = paso(a, "10");
        assertTrue(tiene("PRIMEROS_PASOS"), "el primer paso lo da");
        paso(b, "10");

        registroService.deshacerRegistro((Integer) pasoA.get("registroId"));
        assertFalse(tiene("PRIMEROS_PASOS"), "deshacer el paso que lo dio lo retira");

        paso(a, "10");
        assertTrue(tiene("PRIMEROS_PASOS"), "con dos registros ya ha dado algún paso");
    }
}
