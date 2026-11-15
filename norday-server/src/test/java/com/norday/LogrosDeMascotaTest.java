package com.norday;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
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
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Los logros de la mascota (MASCOTA_CRIA, MASCOTA_ADULTO) se tienen cuando
 * la mascota está en esa fase o en una superior, y se miran cada vez que
 * gana XP, no solo cuando cambia de fase. Si no:
 * - deshacer retira MASCOTA_CRIA (va en la reversión del completado que lo
 *   dio), la XP de otro hábito la mantiene en cría, y como la fase ya no
 *   cambia, el logro no vuelve nunca;
 * - un salto de huevo a adulta de una vez no daba MASCOTA_CRIA.
 *
 * "Hoy" es el viernes 9-oct-2026, fijado en RachaService.hoyDe. La cría
 * llega a los 70 XP (nivel 3) y la adulta a los 350 (nivel 11); un paso de
 * un diario de veces da 5.
 */
@SpringBootTest
@Transactional
class LogrosDeMascotaTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Autowired private RegistroService registroService;
    @Autowired private MascotaService mascotaService;
    @Autowired private IUsuarioLogroDAO usuarioLogroDAO;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private Usuario usuario;

    private void unUsuarioConXp(int xp) {
        usuario = new Usuario();
        usuario.setNombre("Test Logros Mascota");
        usuario.setUsername("test-mascota-" + System.nanoTime());
        usuario.setEmail("test-mascota-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);
        Mascota mascota = mascotaService.obtenerOCrear(usuario.getUsuarioId());
        mascota.setExperiencia(xp);
        em.flush();
        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    private Habito unDiario(String nombre) {
        Habito habito = new Habito(nombre, null, Frecuencia.DIARIO, 1, usuario, null, INICIO);
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, new Objetivo(Frecuencia.DIARIO, 1, null)));
        em.persist(new Racha(habito, VIERNES));
        em.persist(new Ritmo(habito, VIERNES));
        em.flush();
        return habito;
    }

    private Map<String, Object> paso(Habito habito) {
        em.flush();
        em.clear();
        return registroService.completar(em.find(Habito.class, habito.getHabitoId()),
                new Solicitud(null, VIERNES, null, null, OrigenRegistro.MANUAL));
    }

    private boolean tiene(String codigo) {
        em.flush();
        return usuarioLogroDAO.findByUsuario(em.find(Usuario.class, usuario.getUsuarioId())).stream()
                .anyMatch(ul -> codigo.equals(ul.getLogro().getCodigo()));
    }

    @Test
    void deshacerRetiraLaCria_laXpDeOtroHabitoLaMantiene_yElSiguientePasoLaDevuelve() {
        unUsuarioConXp(65);
        Habito a = unDiario("A");
        Habito b = unDiario("B");
        Habito c = unDiario("C");

        Map<String, Object> pasoA = paso(a);
        assertTrue(tiene("MASCOTA_CRIA"), "A la lleva a 70: cría");
        paso(b);

        registroService.deshacerRegistro((Integer) pasoA.get("registroId"));
        assertFalse(tiene("MASCOTA_CRIA"), "deshacer A retira el logro que dio");
        assertEquals(70, mascotaService.obtenerOCrear(usuario.getUsuarioId()).getExperiencia(),
                "con la XP de B sigue siendo cría");

        paso(c);
        assertTrue(tiene("MASCOTA_CRIA"), "sigue en cría: el siguiente paso lo devuelve");
    }

    private Habito unDiarioDeMinutos(String nombre, int minutos) {
        Objetivo objetivo = new Objetivo(com.norday.habitos.model.Unidad.MINUTOS, Frecuencia.DIARIO, 1, minutos, null);
        Habito habito = new Habito(nombre, null, Frecuencia.DIARIO, 1, usuario, null, INICIO);
        habito.setUnidad(com.norday.habitos.model.Unidad.MINUTOS);
        habito.setMinutosObjetivo(minutos);
        em.persist(habito);
        em.persist(new HabitoObjetivo(habito, INICIO, objetivo));
        em.persist(new Racha(habito, VIERNES));
        em.persist(new Ritmo(habito, VIERNES));
        em.flush();
        return habito;
    }

    private Map<String, Object> minutos(Habito habito, int minutos) {
        em.flush();
        em.clear();
        return registroService.completar(em.find(Habito.class, habito.getHabitoId()),
                new Solicitud(null, VIERNES, String.valueOf(minutos), null, OrigenRegistro.MANUAL));
    }

    private int xp() {
        em.flush();
        em.clear();
        return mascotaService.obtenerOCrear(usuario.getUsuarioId()).getExperiencia();
    }

    @Test
    void unPasoSinXp_tambienDevuelveLaCriaSiLaFaseLaMerece() {
        unUsuarioConXp(69);
        Habito a = unDiarioDeMinutos("A", 30);
        Habito b = unDiarioDeMinutos("B", 30);

        Map<String, Object> pasoA = minutos(a, 15);
        assertEquals(71, xp());
        assertTrue(tiene("MASCOTA_CRIA"), "A la lleva a 71: cría");
        minutos(b, 30);
        assertEquals(76, xp());

        registroService.deshacerRegistro((Integer) pasoA.get("registroId"));
        assertEquals(74, xp(), "con la XP de B sigue en cría");
        assertFalse(tiene("MASCOTA_CRIA"), "deshacer A retira el logro que dio");

        // Minutos de más en B: el día ya estaba hecho, el paso no da XP.
        Map<String, Object> exceso = minutos(b, 20);
        assertEquals(0, exceso.get("xpGanada"));
        assertTrue(tiene("MASCOTA_CRIA"), "sigue en cría: un paso aunque sea sin XP lo devuelve");

        // Y queda en la reversión de ese paso, como cualquier otro logro.
        registroService.deshacerRegistro((Integer) exceso.get("registroId"));
        assertFalse(tiene("MASCOTA_CRIA"), "deshacer el paso sin XP retira lo que devolvió");
    }

    @Test
    void deHuevoAAdultaDeUnaVez_daLosDos() {
        unUsuarioConXp(0);
        mascotaService.ganarExperiencia(usuario.getUsuarioId(), 400);
        assertAll(
                () -> assertTrue(tiene("MASCOTA_ADULTO")),
                () -> assertTrue(tiene("MASCOTA_CRIA"), "pasar por la cría también cuenta"));
    }
}
