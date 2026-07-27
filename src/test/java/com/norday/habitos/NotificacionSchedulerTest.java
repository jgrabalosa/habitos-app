package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.NotificacionScheduler;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El recordatorio se dispara contra la hora local del usuario, no la del
 * servidor: es el escenario que la fase tenía que arreglar.
 */
@ExtendWith(MockitoExtension.class)
class NotificacionSchedulerTest {

    private static final ZoneId SAO_PAULO = ZoneId.of("America/Sao_Paulo");
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IUsuarioDAO usuarioDAO;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private NotificacionScheduler notificacionScheduler;

    private Habito habitoCon(ZoneId zona, LocalTime horaRecordatorio) {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setZonaHoraria(zona.getId());
        usuario.setFcmToken("token-fcm");

        Habito habito = new Habito();
        habito.setHabitoId(10);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);
        habito.setRecordatorioActivo(true);
        habito.setRecordatorioHora(horaRecordatorio);
        return habito;
    }

    @Test
    void usuarioEnZonaNoEuropea_recibeElRecordatorioASuHoraLocal() {
        // Hábito cuya hora de recordatorio es justo la ventana actual EN SAO PAULO
        LocalTime ahoraEnSaoPaulo = LocalTime.now(SAO_PAULO).withSecond(0).withNano(0);
        LocalTime enVentana = ahoraEnSaoPaulo.withMinute((ahoraEnSaoPaulo.getMinute() / 5) * 5);

        Habito habito = habitoCon(SAO_PAULO, enVentana);
        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(SAO_PAULO);
        when(registroDAO.findByFecha(any())).thenReturn(new ArrayList<>());

        notificacionScheduler.enviarRecordatorios();

        verify(notificacionService).enviarNotificacion(eq("token-fcm"), anyString(), anyString());
    }

    @Test
    void mismaHoraDeRecordatorio_noSeDisparaSiEnSuRelojNoToca() {
        // La hora del recordatorio corresponde a la ventana de Madrid, pero el
        // usuario está en Sao Paulo: en su reloj aún no es esa hora.
        LocalTime ahoraEnMadrid = LocalTime.now(MADRID).withSecond(0).withNano(0);
        LocalTime ventanaMadrid = ahoraEnMadrid.withMinute((ahoraEnMadrid.getMinute() / 5) * 5);

        Habito habito = habitoCon(SAO_PAULO, ventanaMadrid);
        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(SAO_PAULO);

        notificacionScheduler.enviarRecordatorios();

        // Madrid y Sao Paulo no comparten hora local, así que no debe enviarse
        verify(notificacionService, never()).enviarNotificacion(anyString(), anyString(), anyString());
    }

    @Test
    void sinHabitosActivos_noSeConsultaNadaMas() {
        when(habitoDAO.findTodosActivos()).thenReturn(new ArrayList<>());

        notificacionScheduler.enviarRecordatorios();

        verify(registroDAO, never()).findByFecha(any());
        verify(notificacionService, never()).enviarNotificacion(anyString(), anyString(), anyString());
    }
}
