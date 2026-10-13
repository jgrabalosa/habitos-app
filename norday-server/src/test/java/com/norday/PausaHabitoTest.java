package com.norday;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.CumplimientoDiarioHabitos;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.PausaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RitmoService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pausar y reanudar un hábito, con la aplicación ensamblada y la base de
 * datos. Pausar gasta una «Pausa» y congela el ritmo; reanudar es gratis y
 * los días de pausa no cuentan. El ancla se pone en el "hoy" real, como en
 * CambiarHabitoTest, y los días posteriores se pasan a mano.
 */
@SpringBootTest
@Transactional
class PausaHabitoTest {

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private PausaService pausaService;

    @Autowired
    private CumplimientoDiarioHabitos cumplimientoDiario;

    @Autowired
    private RitmoService ritmoService;

    @Autowired
    private RegistroService registroService;

    @Autowired
    private IProductoDAO productoDAO;

    @PersistenceContext
    private EntityManager em;

    private final LocalDate hoy = LocalDate.now(ZoneId.of("Europe/Madrid"));
    private int usuarioId;
    private int habitoId;

    /** Un diario de una vez al día, creado hace un mes, con el ritmo en 0,8. */
    @BeforeEach
    void unDiarioConRitmo() {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Pausa");
        usuario.setUsername("test-pausa-" + System.nanoTime());
        usuario.setEmail("test-pausa-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Leer", "Descripción", Frecuencia.DIARIO, 1,
                usuario, null, hoy.minusDays(30));
        em.persist(habito);
        em.persist(new Racha(habito, hoy));
        Ritmo ritmo = new Ritmo(habito, hoy);
        ritmo.setValor(0.8);
        em.persist(ritmo);
        em.flush();
        em.clear();

        usuarioId = usuario.getUsuarioId();
        habitoId = habito.getHabitoId();
    }

    private void darPausas(int cantidad) {
        Producto pausa = productoDAO.findByCodigo("PAUSA_HABITO");
        em.persist(new UsuarioProducto(em.find(Usuario.class, usuarioId), pausa, cantidad));
        em.flush();
    }

    private int pausasQueQuedan() {
        em.flush();
        em.clear();
        return em.createQuery(
                        "SELECT up.cantidad FROM UsuarioProducto up "
                                + "WHERE up.usuario.usuarioId = :usuarioId "
                                + "AND up.producto.codigo = 'PAUSA_HABITO'", Integer.class)
                .setParameter("usuarioId", usuarioId)
                .getSingleResult();
    }

    private Habito habito() {
        em.flush();
        em.clear();
        return em.find(Habito.class, habitoId);
    }

    private Ritmo ritmoGuardado() {
        em.flush();
        em.clear();
        return em.createQuery(
                        "SELECT r FROM Ritmo r WHERE r.habito.habitoId = :habitoId", Ritmo.class)
                .setParameter("habitoId", habitoId)
                .getSingleResult();
    }

    @Test
    void sinPausa_tresSemanasSinHacerlo_elRitmoBaja() {
        // Control: sin esto, el resto no demostraría nada.
        assertTrue(ritmoService.ritmoLeido(habito(), hoy.plusDays(21)).valor() < 0.8);
    }

    @Test
    void pausar_gastaUna_dejaElHabitoEnPausa_yAnclaElRitmoEnHoy() {
        darPausas(2);

        pausaService.pausar(habitoId);

        assertEquals(1, pausasQueQuedan());
        assertFalse(habito().isActivo());
        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(hoy, ritmo.getDesde());
    }

    @Test
    void pausar_sinPausas_lanzaConflicto_yElHabitoSigueActivo() {
        assertThrows(ConflictoException.class, () -> pausaService.pausar(habitoId));
        assertTrue(habito().isActivo());
    }

    @Test
    void pausar_unoYaPausado_lanzaConflicto_yNoGastaOtra() {
        darPausas(2);
        pausaService.pausar(habitoId);

        assertThrows(ConflictoException.class, () -> pausaService.pausar(habitoId));
        assertEquals(1, pausasQueQuedan());
    }

    @Test
    void enPausa_elRitmoNoBaja_niSeGuardaBajado() {
        darPausas(1);
        pausaService.pausar(habitoId);

        assertEquals(0.8, ritmoService.ritmoLeido(habito(), hoy.plusDays(21)).valor(), 0.0);
        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(hoy, ritmo.getDesde());
    }

    @Test
    void alReanudar_losDiasDePausaNoCuentan() {
        darPausas(1);
        pausaService.pausar(habitoId);
        LocalDate vuelta = hoy.plusDays(21);

        pausaService.reanudar(habito(), vuelta);

        assertTrue(habito().isActivo());
        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(vuelta, ritmo.getDesde());
        assertEquals(vuelta, ritmo.getCambio());
        assertEquals(0.8, ritmoService.ritmoLeido(habito(), vuelta).valor(), 0.0);
    }

    @Test
    void reanudar_esGratis() {
        darPausas(1);
        pausaService.pausar(habitoId);

        pausaService.reanudar(habitoId);

        assertTrue(habito().isActivo());
        assertEquals(0, pausasQueQuedan());
    }

    @Test
    void unHabitoEnPausa_noSePuedeMarcar() {
        darPausas(1);
        pausaService.pausar(habitoId);

        assertThrows(ConflictoException.class,
                () -> registroService.completarHabito(habito(), null));
    }

    @Test
    void editarUnHabitoEnPausa_noLeTocaElRitmo() {
        darPausas(1);
        pausaService.pausar(habitoId);

        // Lo que hace editar el objetivo o la frecuencia, tres semanas después.
        ritmoService.anclarAntesDeCambiar(habito(), hoy.plusDays(21));

        Ritmo ritmo = ritmoGuardado();
        assertEquals(0.8, ritmo.getValor(), 0.0);
        assertEquals(hoy, ritmo.getDesde());
    }

    @Test
    void unRegistroDeUnHabitoEnPausa_noSePuedeDeshacer() {
        Map<String, Object> hecho = registroService.completarHabito(habito(), null);
        int registroId = (Integer) hecho.get("registroId");
        darPausas(1);
        pausaService.pausar(habitoId);

        assertThrows(ConflictoException.class,
                () -> registroService.deshacerRegistro(registroId));
    }

    @Test
    void conTodosLosHabitosEnPausa_noriQuedaDormida_yNoTriste() {
        darPausas(1);
        pausaService.pausar(habitoId);
        Usuario usuario = em.find(Usuario.class, usuarioId);

        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimientoDiario.comoVaHoy(
                usuario, hoy.plusDays(10), ZoneId.of("Europe/Madrid")));
    }
}
