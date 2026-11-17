package com.norday.enfoque;

import com.norday.core.model.Usuario;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.enfoque.service.LogrosEnfoqueService;
import com.norday.enfoque.service.PremioEnfoqueService;
import com.norday.enfoque.service.PremioEnfoqueService.Premio;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El premio de una sesión de Norday Focus: cuánto paga, el tope del día, lo
 * que se apunta en la sesión y los logros nuevos como «después − antes».
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class PremioEnfoqueServiceTest {

    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Mock private ISesionEnfoqueDAO sesionDAO;
    @Mock private UsuarioMonedaService usuarioMonedaService;
    @Mock private MascotaService mascotaService;
    @Mock private LogroService logroService;
    @Mock private LogrosEnfoqueService logrosEnfoqueService;

    private PremioEnfoqueService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        servicio = new PremioEnfoqueService();
        ReflectionTestUtils.setField(servicio, "sesionDAO", sesionDAO);
        ReflectionTestUtils.setField(servicio, "usuarioMonedaService", usuarioMonedaService);
        ReflectionTestUtils.setField(servicio, "mascotaService", mascotaService);
        ReflectionTestUtils.setField(servicio, "logroService", logroService);
        ReflectionTestUtils.setField(servicio, "logrosEnfoqueService", logrosEnfoqueService);
        usuario = new Usuario();
        usuario.setUsuarioId(7);
        pagadoHoy(0, 0);
        when(logroService.logrosDeUsuario(usuario)).thenReturn(List.of());
    }

    private void pagadoHoy(int monedas, int xp) {
        when(sesionDAO.premiadoDelDia(7, VIERNES)).thenReturn(new int[]{monedas, xp});
    }

    private static SesionEnfoque sesion(EstadoSesion estado, int minutos) {
        SesionEnfoque s = new SesionEnfoque();
        s.setId(41);
        s.setEstado(estado);
        s.setDuracionSeg(minutos * 60 + 30);
        s.setDuracionObjetivoMin(25);
        s.setDia(VIERNES);
        return s;
    }

    private static List<UsuarioLogro> logros(String... codigos) {
        return Arrays.stream(codigos).map(c -> {
            Logro logro = new Logro();
            logro.setCodigo(c);
            return new UsuarioLogro(null, logro);
        }).toList();
    }

    @Test
    void completada_unaMonedaPorMinuto_masElRemate_yUnaXpCadaCinco() {
        SesionEnfoque s = sesion(EstadoSesion.COMPLETADA, 25);

        Premio premio = servicio.pagar(usuario, s);

        assertEquals(new Premio(30, 5, List.of(), false), premio);
        verify(usuarioMonedaService).registrarMovimiento(eq(usuario), eq(30), eq("SESION_ENFOQUE"), eq(41), anyString());
        verify(mascotaService).ganarExperiencia(7, 5);
        verify(logrosEnfoqueService).evaluar(usuario, s, false);
        verify(sesionDAO).guardarPremio(41, 30, 5, null, false);
    }

    @Test
    void terminada_susMinutos_sinRemate() {
        Premio premio = servicio.pagar(usuario, sesion(EstadoSesion.TERMINADA, 24));
        assertEquals(24, premio.monedas());
        assertEquals(4, premio.xp(), "24 / 5, hacia abajo");
    }

    @Test
    void perdida_noPagaNada_niApuntaNada() {
        Premio premio = servicio.pagar(usuario, sesion(EstadoSesion.PERDIDA, 20));
        assertEquals(Premio.NADA, premio);
        verifyNoInteractions(usuarioMonedaService, mascotaService, logrosEnfoqueService);
        verify(sesionDAO, never()).guardarPremio(anyInt(), anyInt(), anyInt(), any(), anyBoolean());
    }

    @Test
    void elTope_recortaLoQueFalta_yLoDice() {
        pagadoHoy(100, 20);
        SesionEnfoque s = sesion(EstadoSesion.COMPLETADA, 25);

        Premio premio = servicio.pagar(usuario, s);

        assertEquals(20, premio.monedas(), "faltaban 20 para 120");
        assertEquals(4, premio.xp(), "faltaban 4 para 24");
        assertTrue(premio.topeAlcanzado());
        verify(logrosEnfoqueService).evaluar(usuario, s, true);
        verify(sesionDAO).guardarPremio(41, 20, 4, null, true);
    }

    @Test
    void llegarJustoAlTope_cuentaComoAlcanzado() {
        pagadoHoy(90, 18);
        Premio premio = servicio.pagar(usuario, sesion(EstadoSesion.COMPLETADA, 25));
        assertEquals(30, premio.monedas());
        assertTrue(premio.topeAlcanzado());
    }

    @Test
    void conElTopeYaAlcanzado_noPaga_peroMiraLosLogros_yLoApunta() {
        pagadoHoy(120, 24);
        SesionEnfoque s = sesion(EstadoSesion.COMPLETADA, 25);

        Premio premio = servicio.pagar(usuario, s);

        assertEquals(new Premio(0, 0, List.of(), true), premio);
        verifyNoInteractions(usuarioMonedaService, mascotaService);
        verify(logrosEnfoqueService).evaluar(usuario, s, true);
        verify(sesionDAO).guardarPremio(41, 0, 0, null, true);
    }

    @Test
    void conElTopeDeMonedasAlcanzado_tampocoPagaXp_aunqueQuedeXp() {
        pagadoHoy(120, 20);
        Premio premio = servicio.pagar(usuario, sesion(EstadoSesion.COMPLETADA, 25));
        assertEquals(new Premio(0, 0, List.of(), true), premio);
        verifyNoInteractions(mascotaService);
    }

    @Test
    void conTerminadas_losDosTopesLleganJuntos_a120Minutos() {
        // 100 minutos terminados: 100 monedas y 20 XP; quedan 20 y 4.
        pagadoHoy(100, 20);
        Premio premio = servicio.pagar(usuario, sesion(EstadoSesion.TERMINADA, 30));
        assertEquals(20, premio.monedas());
        assertEquals(4, premio.xp());
        assertTrue(premio.topeAlcanzado());
    }

    @Test
    void lejosDelTope_noEsAlcanzado() {
        pagadoHoy(30, 5);
        assertFalse(servicio.pagar(usuario, sesion(EstadoSesion.COMPLETADA, 25)).topeAlcanzado());
    }

    @Test
    void losLogros_sonLosNuevos_tambienLosDeFaseDeNori_enOrden() {
        when(logroService.logrosDeUsuario(usuario))
                .thenReturn(logros("BIENVENIDO"))
                .thenReturn(logros("BIENVENIDO", "MASCOTA_CRIA", "ENFOQUE_PRIMERA_SESION"));

        Premio premio = servicio.pagar(usuario, sesion(EstadoSesion.COMPLETADA, 25));

        assertEquals(List.of("MASCOTA_CRIA", "ENFOQUE_PRIMERA_SESION"), premio.logros());
        verify(sesionDAO).guardarPremio(41, 30, 5, "MASCOTA_CRIA,ENFOQUE_PRIMERA_SESION", false);
    }

    @Test
    void elPremioGuardado_esElDeLaSesion() {
        SesionEnfoque s = sesion(EstadoSesion.COMPLETADA, 25);
        s.setPremioMonedas(12);
        s.setPremioXp(3);
        s.setPremioLogros("ENFOQUE_DIA");
        s.setPremioTope(true);
        assertEquals(new Premio(12, 3, List.of("ENFOQUE_DIA"), true), PremioEnfoqueService.guardado(s));
        assertEquals(Premio.NADA, PremioEnfoqueService.guardado(sesion(EstadoSesion.PERDIDA, 5)),
                "una sesión de antes de V27 se queda sin premio");
    }
}
