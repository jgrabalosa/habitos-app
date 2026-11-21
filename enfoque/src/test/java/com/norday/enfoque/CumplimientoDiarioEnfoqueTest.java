package com.norday.enfoque;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.enfoque.service.CumplimientoDiarioEnfoque;
import com.norday.enfoque.service.RelojEnfoque;
import com.norday.gamificacion.model.AvanceDeHoy;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static com.norday.gamificacion.model.AvanceDeHoy.AUSENTE;
import static com.norday.gamificacion.model.AvanceDeHoy.SIN_EMPEZAR;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Cómo va hoy el usuario en Norday Focus, para el ánimo de Nori. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class CumplimientoDiarioEnfoqueTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    /** Viernes 9-oct-2026, 00:30 en Madrid: todavía jueves en UTC. */
    private static final Instant AHORA = Instant.parse("2026-10-08T22:30:00Z");

    @Mock private ISesionEnfoqueDAO sesionDAO;
    @Mock private IUsuarioDAO usuarioDAO;
    @Mock private ZonaUsuarioService zonaUsuarioService;
    @Mock private RelojEnfoque reloj;

    private CumplimientoDiarioEnfoque cumplimiento;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        cumplimiento = new CumplimientoDiarioEnfoque();
        ReflectionTestUtils.setField(cumplimiento, "sesionDAO", sesionDAO);
        ReflectionTestUtils.setField(cumplimiento, "usuarioDAO", usuarioDAO);
        ReflectionTestUtils.setField(cumplimiento, "zonaUsuarioService", zonaUsuarioService);
        ReflectionTestUtils.setField(cumplimiento, "reloj", reloj);
        usuario = new Usuario();
        usuario.setUsuarioId(7);
        // Alta hace mucho: no es un recién llegado.
        usuario.setFechaRegistro(LocalDateTime.of(2026, 1, 1, 10, 0));
        when(usuarioDAO.findById(7)).thenReturn(usuario);
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(MADRID);
        when(reloj.ahora()).thenReturn(AHORA);
    }

    @Test
    void hoy_esElDelUsuario_enSuZona() {
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(50);
        assertEquals(AvanceDeHoy.COMPLETO, cumplimiento.comoVaHoy(7));
        assertTrue(cumplimiento.hoyCumplido(7));
    }

    @Test
    void completo_desdeElDiaDeEnfoque_empezado_conAlgo() {
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(49);
        assertEquals(AvanceDeHoy.EMPEZADO, cumplimiento.comoVaHoy(7));
        assertFalse(cumplimiento.hoyCumplido(7));
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(10);
        assertEquals(AvanceDeHoy.EMPEZADO, cumplimiento.comoVaHoy(7));
    }

    @Test
    void ausente_conTresDiasSinSesion_comoEnHabits() {
        // Última el lunes: martes, miércoles y jueves sin nada.
        when(sesionDAO.ultimoDiaQueCuentaAntesDe(7, VIERNES)).thenReturn(VIERNES.minusDays(4));
        assertEquals(AvanceDeHoy.AUSENTE, cumplimiento.comoVaHoy(7));
        // Última el martes: dos días sin nada.
        when(sesionDAO.ultimoDiaQueCuentaAntesDe(7, VIERNES)).thenReturn(VIERNES.minusDays(3));
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(7));
    }

    @Test
    void sinSesiones_cuentaDesdeElAlta() {
        assertEquals(AvanceDeHoy.AUSENTE, cumplimiento.comoVaHoy(7), "alta hace meses, ninguna sesión");
        usuario.setFechaRegistro(LocalDateTime.of(2026, 10, 7, 10, 0));
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(7), "recién llegado: dormida, no triste");
    }

    private static final LocalDate SABADO = VIERNES.plusDays(1);

    @Test
    void proximosDias_conSesionHoy_cuentanDesdeHoy() {
        // Hoy, viernes, 20 minutos: la última sesión es hoy. Sábado, domingo
        // y lunes con 0, 1 y 2 días sin nada; el martes, el tercero.
        when(sesionDAO.ultimoDiaQueCuentaAntesDe(7, SABADO)).thenReturn(VIERNES);
        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, AUSENTE, AUSENTE, AUSENTE, AUSENTE),
                cumplimiento.comoVaLosProximosDias(7, 7));
    }

    @Test
    void proximosDias_sinSesionesYRecienDadoDeAlta_cuentanDesdeElAlta() {
        // Alta el miércoles 7: el sábado lleva dos días enteros sin nada
        // (jueves y viernes); el domingo, tres.
        usuario.setFechaRegistro(LocalDateTime.of(2026, 10, 7, 10, 0));
        assertEquals(List.of(SIN_EMPEZAR, AUSENTE, AUSENTE),
                cumplimiento.comoVaLosProximosDias(7, 3));
    }

    @Test
    void proximosDias_elDiaQuePasaAAusente_esElTerceroSinSesion() {
        // Última sesión el jueves 8: viernes y sábado y domingo no llegan a
        // tres; el lunes 12 sí.
        when(sesionDAO.ultimoDiaQueCuentaAntesDe(7, SABADO)).thenReturn(VIERNES.minusDays(1));
        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, AUSENTE, AUSENTE),
                cumplimiento.comoVaLosProximosDias(7, 4));
    }

    @Test
    void proximosDias_cadaDiaEsLoQueDariaComoVaHoyEseDia() {
        when(sesionDAO.ultimoDiaQueCuentaAntesDe(eq(7), any())).thenAnswer(invocacion -> {
            LocalDate antesDe = invocacion.getArgument(1);
            LocalDate ultima = VIERNES.minusDays(2);
            return ultima.isBefore(antesDe) ? ultima : null;
        });
        List<AvanceDeHoy> previstos = cumplimiento.comoVaLosProximosDias(7, 7);
        assertEquals(7, previstos.size());
        for (int i = 1; i <= 7; i++) {
            when(reloj.ahora()).thenReturn(AHORA.plus(i, java.time.temporal.ChronoUnit.DAYS));
            assertEquals(cumplimiento.comoVaHoy(7), previstos.get(i - 1), "día +" + i);
        }
        when(reloj.ahora()).thenReturn(AHORA);
        assertTrue(previstos.contains(SIN_EMPEZAR) && previstos.contains(AUSENTE));
    }

    @Test
    void proximosDias_sinDiasOSinUsuario() {
        assertEquals(List.of(), cumplimiento.comoVaLosProximosDias(7, 0));
        when(usuarioDAO.findById(7)).thenReturn(null);
        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR), cumplimiento.comoVaLosProximosDias(7, 2));
    }

    @Test
    void enUso_conAlgunaSesionEnLosUltimos14Dias() {
        when(sesionDAO.hayQueCuentaDesde(7, VIERNES.minusDays(13))).thenReturn(true);
        assertTrue(cumplimiento.enUso(7));
        when(sesionDAO.hayQueCuentaDesde(7, VIERNES.minusDays(13))).thenReturn(false);
        assertFalse(cumplimiento.enUso(7));
    }

    @Test
    void sinUsuario_noEnUso_niCumplido() {
        when(usuarioDAO.findById(7)).thenReturn(null);
        assertFalse(cumplimiento.enUso(7));
        assertFalse(cumplimiento.hoyCumplido(7));
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(7));
    }
}
