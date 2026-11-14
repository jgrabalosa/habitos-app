package com.norday;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.dto.FilaHoyDTO;
import com.norday.habitos.model.dto.HabitoDetalleDTO;
import com.norday.habitos.model.dto.HoyDTO;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.HoyService;
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
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Lo que pasa después de cambiar un objetivo, con la aplicación ensamblada:
 * el cambio programado entra en vigor al leer pasada su fecha, ancla el
 * ritmo en esa fecha, y cada día —también uno retroactivo de esta semana o
 * uno pasado de la tira de Hoy— se juzga con el objetivo que tenía.
 *
 * Semana de referencia: lunes 5 a domingo 11 de octubre de 2026. El "hoy"
 * de HabitoService.actualizar y de RegistroService se fija en
 * RachaService.hoyDe; el de Hoy y el del ritmo se pasa a mano.
 */
@SpringBootTest
@Transactional
class CambioProgramadoTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 10);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 11);

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private RegistroService registroService;

    @Autowired
    private HoyService hoyService;

    @Autowired
    private RitmoService ritmoService;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private int usuarioId;
    private int habitoId;

    /** Un diario de una vez al día, con el ritmo en 0,5 desde el lunes. */
    private void unDiario() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Programado");
        usuario.setUsername("test-programado-" + System.nanoTime());
        usuario.setEmail("test-programado-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Leer", "Descripción", Frecuencia.DIARIO, 1, usuario, null, INICIO);
        em.persist(habito);
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        usuarioId = usuario.getUsuarioId();
        habitoId = habito.getHabitoId();
    }

    private void hoyEs(LocalDate dia) {
        doReturn(dia).when(rachaService).hoyDe(any(Habito.class));
    }

    private void registros(LocalDate... fechas) {
        Habito habito = em.find(Habito.class, habitoId);
        for (LocalDate fecha : fechas) {
            em.persist(new Registro(habito, true, null, fecha));
        }
        em.flush();
        em.clear();
    }

    private Habito conMeta(int meta) {
        Habito habito = new Habito();
        habito.setHabitoId(habitoId);
        habito.setNombre("Leer");
        habito.setDescripcion("Descripción");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(meta);
        habito.setPropietario(em.find(Usuario.class, usuarioId));
        return habito;
    }

    private Habito habito() {
        em.flush();
        em.clear();
        return em.find(Habito.class, habitoId);
    }

    private Usuario usuario() {
        return em.find(Usuario.class, usuarioId);
    }

    private Ritmo ritmo() {
        em.flush();
        em.clear();
        return em.createQuery("SELECT r FROM Ritmo r WHERE r.habito.habitoId = :id", Ritmo.class)
                .setParameter("id", habitoId)
                .getSingleResult();
    }

    private FilaHoyDTO filaDelDia(HoyDTO hoy) {
        return hoy.delDia().habitos().stream()
                .filter(fila -> fila.habito().getHabitoId() == habitoId)
                .findFirst()
                .orElseThrow();
    }

    @Test
    void elCambioProgramado_seAplicaAlLeerPasadaSuFecha_yAnclaElRitmoConLasReglasAntiguas() {
        unDiario();
        List<LocalDate> semana = List.of(LUNES, MARTES, MARTES.plusDays(1), JUEVES, VIERNES);
        registros(semana.toArray(LocalDate[]::new));
        hoyEs(VIERNES);

        assertEquals(SABADO, habitoService.actualizar(conMeta(2), null));
        assertEquals(1, habito().getMeta());

        // El viernes todavía no ha llegado: Hoy sigue con la meta de hoy y el
        // ritmo no se toca.
        FilaHoyDTO viernes = filaDelDia(hoyService.obtenerHoy(usuario(), VIERNES, VIERNES));
        assertEquals(1, viernes.meta());
        assertEquals(1, habito().getMeta());
        assertNull(ritmo().getCambio());

        // El sábado, la primera lectura lo aplica.
        FilaHoyDTO sabado = filaDelDia(hoyService.obtenerHoy(usuario(), SABADO, SABADO));
        assertEquals(2, sabado.meta());
        assertEquals(2, habito().getMeta());

        // El ritmo se ancla en la fecha del cambio, con la semana cerrada con
        // la meta antigua: cinco días de cinco, no cinco medios días.
        Ritmo ritmo = ritmo();
        double conLasReglasAntiguas = CalculoRitmo.alDia(0.5, LUNES, Frecuencia.DIARIO, 1,
                INICIO, semana, SABADO).valor();
        double conLasNuevas = CalculoRitmo.alDia(0.5, LUNES, Frecuencia.DIARIO, 2,
                INICIO, semana, SABADO).valor();
        assertEquals(SABADO, ritmo.getDesde());
        assertEquals(SABADO, ritmo.getCambio());
        assertEquals(conLasReglasAntiguas, ritmo.getValor(), 1e-9);
        assertTrue(conLasReglasAntiguas > conLasNuevas);

        // El periodo del cambio solo puede subir: el sábado sin hacer nada no
        // lo baja.
        assertEquals(conLasReglasAntiguas, ritmoService.ritmoVigente(habito(), DOMINGO), 1e-9);
    }

    @Test
    void elCambioProgramado_seAplicaAntesDeCompletar() {
        unDiario();
        registros(VIERNES);
        hoyEs(VIERNES);
        habitoService.actualizar(conMeta(2), null);

        hoyEs(SABADO);
        Map<String, Object> primero = registroService.completarHabito(habito(), "", SABADO);

        // Meta 2: el primer paso cobra la mitad del día.
        assertEquals(12, primero.get("puntosPaso"));
        assertEquals(2, habito().getMeta());
        assertEquals(SABADO, ritmo().getCambio());
    }

    @Test
    void unCompletadoRetroactivo_usaLaMetaDeSuFecha() {
        // El jueves, sin registros ese día, la meta sube a 2 desde el jueves.
        unDiario();
        registros(LUNES);
        hoyEs(JUEVES);
        assertEquals(JUEVES, habitoService.actualizar(conMeta(2), null));
        assertEquals(2, habito().getMeta());

        // El viernes se marca el martes: su meta era 1, así que una vez
        // cierra el día entero y una segunda se rechaza.
        hoyEs(VIERNES);
        Map<String, Object> martes = registroService.completarHabito(habito(), "", MARTES);
        assertEquals(25, martes.get("puntosPaso"));
        assertThrows(ConflictoException.class,
                () -> registroService.completarHabito(habito(), "", MARTES));

        // Y el viernes, con la meta nueva, cobra medio día.
        Map<String, Object> viernes = registroService.completarHabito(habito(), "", VIERNES);
        assertEquals(12, viernes.get("puntosPaso"));
    }

    @Test
    void hoyDeUnDiaPasado_loJuzgaConElObjetivoDeEseDia() {
        unDiario();
        registros(LUNES, MARTES);
        hoyEs(JUEVES);
        habitoService.actualizar(conMeta(2), null);
        registros(VIERNES);

        HoyDTO martes = hoyService.obtenerHoy(usuario(), MARTES, VIERNES);
        FilaHoyDTO filaMartes = filaDelDia(martes);
        assertEquals(1, filaMartes.meta());
        assertTrue(filaMartes.hecho());

        HoyDTO viernes = hoyService.obtenerHoy(usuario(), VIERNES, VIERNES);
        FilaHoyDTO filaViernes = filaDelDia(viernes);
        assertEquals(2, filaViernes.meta());
        assertFalse(filaViernes.hecho());

        // La tira: el martes, hecho con su meta de entonces; el viernes, a medias.
        assertEquals(1.0, viernes.dias().get(1).avance(), 1e-9);
        assertEquals(0.5, viernes.dias().get(4).avance(), 1e-9);
    }

    @Test
    void elDetalle_diceElCambioProgramado_ySoloSiLoHay() {
        // El detalle lee el reloj real: la versión futura se pone a mano,
        // a tres días de hoy.
        unDiario();
        LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
        Habito habito = habito();
        em.persist(new HabitoObjetivo(habito, INICIO, Objetivo.de(habito)));
        em.flush();

        assertNull(habitoService.obtenerDetalle(habitoId, null).getObjetivoProgramado());

        em.persist(new HabitoObjetivo(em.find(Habito.class, habitoId), hoy.plusDays(3),
                new Objetivo(Frecuencia.SEMANAL, 4, "1,2,4,6")));
        em.flush();
        em.clear();

        HabitoDetalleDTO detalle = habitoService.obtenerDetalle(habitoId, null);
        assertEquals(1, detalle.getMeta());
        assertEquals("DIARIO", detalle.getFrecuencia());
        assertNotNull(detalle.getObjetivoProgramado());
        assertEquals(hoy.plusDays(3), detalle.getObjetivoProgramado().desde());
        assertEquals("VECES", detalle.getObjetivoProgramado().unidad().name());
        assertEquals(Frecuencia.SEMANAL, detalle.getObjetivoProgramado().frecuencia());
        assertEquals(4, detalle.getObjetivoProgramado().meta());
        assertNull(detalle.getObjetivoProgramado().minutosObjetivo());
        assertEquals("1,2,4,6", detalle.getObjetivoProgramado().diasSemana());
    }

    @Test
    void crearUnHabito_leDaSuPrimeraVersionDesdeLaFechaDeInicio() {
        unDiario();
        Habito nuevo = new Habito("Correr", null, Frecuencia.SEMANAL, 2, usuario(), null, null);
        habitoService.crearHabito(nuevo, null);
        em.flush();
        em.clear();

        List<HabitoObjetivo> versiones = em.createQuery(
                        "SELECT o FROM HabitoObjetivo o WHERE o.habito.habitoId = :id", HabitoObjetivo.class)
                .setParameter("id", nuevo.getHabitoId())
                .getResultList();
        assertEquals(1, versiones.size());
        HabitoObjetivo primera = versiones.get(0);
        assertEquals(em.find(Habito.class, nuevo.getHabitoId()).getFechaInicio(), primera.getDesde());
        assertEquals(Frecuencia.SEMANAL, primera.getFrecuencia());
        assertEquals(2, primera.getMeta());
        assertEquals("VECES", primera.getUnidad().name());
        assertNull(primera.getMinutosObjetivo());
        assertNotNull(primera.getCreadoEn());
    }
}
