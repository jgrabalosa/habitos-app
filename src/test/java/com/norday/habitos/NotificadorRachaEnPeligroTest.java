package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.service.NotificadorRachaEnPeligro;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class NotificadorRachaEnPeligroTest {

    @Mock private IHabitoDAO habitoDAO;
    @Mock private IRachaDAO rachaDAO;
    @Mock private NotificacionService notificacionService;
    @Mock private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private NotificadorRachaEnPeligro notificador;

    private Habito habito;
    private Racha racha;

    /** Zona artificial en la que ahora mismo son las 21:xx, sea cual sea la hora real. */
    private ZoneId zonaDondeSonLasNueve() {
        int horaUtc = LocalTime.now(ZoneOffset.UTC).getHour();
        int desfase = Math.floorMod(21 - horaUtc, 24);
        return ZoneOffset.ofHours(desfase > 12 ? desfase - 24 : desfase);
    }

    @BeforeEach
    void setUp() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setFcmToken("token-fcm");

        habito = new Habito();
        habito.setHabitoId(10);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);

        racha = new Racha(habito, LocalDate.now(ZoneOffset.UTC));
        lenient().when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
    }

    @Test
    void aLas21EnSuZona_conRachaVivaSinRenovar_seAvisa() {
        ZoneId zona = zonaDondeSonLasNueve();
        racha.setRachaActual(4);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().inicioPeriodoAnterior(zona));

        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(zona);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);

        notificador.avisarRachasEnPeligro();

        verify(notificacionService).enviarNotificacion(eq("token-fcm"), anyString(), anyString());
    }

    @Test
    void siYaRenovoLaRachaHoy_noSeMolestaAlUsuario() {
        ZoneId zona = zonaDondeSonLasNueve();
        racha.setRachaActual(4);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().rangoPeriodoActual(zona)[0]);

        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(zona);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);

        notificador.avisarRachasEnPeligro();

        verify(notificacionService, never()).enviarNotificacion(anyString(), anyString(), anyString());
    }

    @Test
    void siEnSuRelojNoSonLas21_noSeAvisaNiSeConsultaLaRacha() {
        ZoneId otraHora = zonaDondeSonLasNueve().getRules()
                .getOffset(java.time.Instant.now()).normalized();
        ZoneId cuatroHorasAntes = ZoneOffset.ofHours(
                ((ZoneOffset) otraHora).getTotalSeconds() / 3600 - 4);

        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(cuatroHorasAntes);

        notificador.avisarRachasEnPeligro();

        verify(rachaDAO, never()).findByHabito(any());
        verify(notificacionService, never()).enviarNotificacion(anyString(), anyString(), anyString());
    }
}
