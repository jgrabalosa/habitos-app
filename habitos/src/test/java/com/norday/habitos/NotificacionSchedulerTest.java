package com.norday.habitos;

import com.norday.core.model.AppNorday;
import com.norday.core.model.Usuario;
import com.norday.core.service.DispositivoService;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.TextosService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.NotificacionScheduler;
import com.norday.habitos.service.ObjetivoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private DispositivoService dispositivoService;

    @Mock
    private NotificacionService notificacionService;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @Mock
    private TextosService textosService;

    @Mock
    private ObjetivoService objetivoService;

    @InjectMocks
    private NotificacionScheduler notificacionScheduler;

    /**
     * El usuario 1 tiene token en Habits y otro en Enfoque. Solo el de Habits
     * es "token-fcm": si el barrido pidiera el de otra app, los tests que
     * esperan el aviso fallarían.
     */
    @BeforeEach
    void tokensPorApp() {
        lenient().when(dispositivoService.tokensDe(anyCollection(), eq(AppNorday.HABITOS)))
                .thenReturn(Map.of(1, "token-fcm"));
        lenient().when(dispositivoService.tokensDe(anyCollection(), eq(AppNorday.ENFOQUE)))
                .thenReturn(Map.of(1, "token-enfoque"));
    }

    private Habito habitoCon(ZoneId zona, LocalTime horaRecordatorio) {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setZonaHoraria(zona.getId());

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
        when(textosService.texto(anyString(), any(Usuario.class), any())).thenReturn("texto");
        when(textosService.texto(anyString(), any(Usuario.class))).thenReturn("texto");

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
        // Ni una llamada, con los textos que sea: anyString() no casa con null.
        verifyNoInteractions(notificacionService);
    }

    @Test
    void sinHabitosActivos_noSeConsultaNadaMas() {
        when(habitoDAO.findTodosActivos()).thenReturn(new ArrayList<>());

        notificacionScheduler.enviarRecordatorios();

        verify(registroDAO, never()).findByFecha(any());
        // Ni una llamada, con los textos que sea: anyString() no casa con null.
        verifyNoInteractions(notificacionService);
    }

    /** La hora de la ventana de 5 minutos en curso en Madrid. */
    private LocalTime ventanaDeMadrid() {
        LocalTime ahora = LocalTime.now(MADRID).withSecond(0).withNano(0);
        return ahora.withMinute((ahora.getMinute() / 5) * 5);
    }

    @Test
    void variosRecordatoriosALaMismaHora_salenEnUnSoloAvisoQueLosNombra() {
        Habito leer = habitoCon(MADRID, ventanaDeMadrid());
        Usuario usuario = leer.getPropietario();
        Habito yoga = habitoCon(MADRID, ventanaDeMadrid());
        yoga.setHabitoId(11);
        yoga.setNombre("Yoga");
        yoga.setPropietario(usuario);

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(leer, yoga));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(textosService.texto("push.recordatorio.varios.titulo", usuario, 2)).thenReturn("titulo de dos");
        when(textosService.texto("push.recordatorio.varios.cuerpo", usuario, "Leer, Yoga")).thenReturn("los nombres");

        notificacionScheduler.enviarRecordatorios();

        verify(notificacionService, times(1)).enviarNotificacion("token-fcm", "titulo de dos", "los nombres");
    }

    @Test
    void unSemanalConLaSemanaYaCumplida_noAvisa() {
        // Una vez por semana, y ya se hizo el lunes de esta semana.
        Habito llamar = habitoCon(MADRID, ventanaDeMadrid());
        llamar.setFrecuencia(Frecuencia.SEMANAL);
        llamar.setMeta(1);
        LocalDate lunes = LocalDate.now(MADRID).with(DayOfWeek.MONDAY);

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(llamar));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(registroDAO.findByHabitosAndRango(anyList(), any(), any()))
                .thenReturn(List.of(new Registro(llamar, true, "", lunes)));

        notificacionScheduler.enviarRecordatorios();

        // Ni una llamada, con los textos que sea: anyString() no casa con null.
        verifyNoInteractions(notificacionService);
    }

    @Test
    void unDiarioAMedias_sigueRecibiendoSuRecordatorio_yLaSemanaSeLeeDesdeElLunes() {
        // Cuatro veces al día y hoy lleva una.
        Habito agua = habitoCon(MADRID, ventanaDeMadrid());
        agua.setMeta(4);
        LocalDate hoy = LocalDate.now(MADRID);

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(agua));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(registroDAO.findByHabitosAndRango(List.of(agua), hoy.with(DayOfWeek.MONDAY), hoy))
                .thenReturn(List.of(new Registro(agua, true, "", hoy)));
        when(textosService.texto(anyString(), any(Usuario.class), any())).thenReturn("texto");
        when(textosService.texto(anyString(), any(Usuario.class))).thenReturn("texto");

        notificacionScheduler.enviarRecordatorios();

        verify(notificacionService).enviarNotificacion(eq("token-fcm"), anyString(), anyString());
    }

    @Test
    void unDiarioDeMinutosAMedias_sigueAvisando_yAlLlegarASusMinutosSeCalla() {
        Habito leer = habitoCon(MADRID, ventanaDeMadrid());
        leer.setUnidad(com.norday.habitos.model.Unidad.MINUTOS);
        leer.setMinutosObjetivo(30);
        LocalDate hoy = LocalDate.now(MADRID);
        Registro veinte = new Registro(leer, true, "", hoy);
        veinte.setCantidad(20);

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(leer));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(registroDAO.findByHabitosAndRango(anyList(), any(), any())).thenReturn(List.of(veinte));
        when(textosService.texto(anyString(), any(Usuario.class), any())).thenReturn("texto");
        when(textosService.texto(anyString(), any(Usuario.class))).thenReturn("texto");

        notificacionScheduler.enviarRecordatorios();
        verify(notificacionService).enviarNotificacion(eq("token-fcm"), anyString(), anyString());

        Registro diez = new Registro(leer, true, "", hoy);
        diez.setCantidad(10);
        when(registroDAO.findByHabitosAndRango(anyList(), any(), any())).thenReturn(List.of(veinte, diez));
        clearInvocations(notificacionService);
        notificacionScheduler.enviarRecordatorios();
        verifyNoInteractions(notificacionService);
    }

    @Test
    void elRecordatorioVaAlTokenDeHabits_nuncaAlDeOtraApp() {
        Habito habito = habitoCon(MADRID, ventanaDeMadrid());
        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(textosService.texto(anyString(), any(Usuario.class), any())).thenReturn("texto");
        when(textosService.texto(anyString(), any(Usuario.class))).thenReturn("texto");

        notificacionScheduler.enviarRecordatorios();

        verify(dispositivoService).tokensDe(anyCollection(), eq(AppNorday.HABITOS));
        verify(notificacionService).enviarNotificacion(eq("token-fcm"), anyString(), anyString());
        verify(notificacionService, never()).enviarNotificacion(eq("token-enfoque"), anyString(), anyString());
    }

    @Test
    void sinTokenDeHabits_noSeEnviaNada() {
        Habito habito = habitoCon(MADRID, ventanaDeMadrid());
        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(dispositivoService.tokensDe(anyCollection(), eq(AppNorday.HABITOS))).thenReturn(Map.of());

        notificacionScheduler.enviarRecordatorios();

        verifyNoInteractions(notificacionService);
    }

    @Test
    void tokenDadoDeBajaEnFcm_seOlvidaElDeHabits() {
        Habito habito = habitoCon(MADRID, ventanaDeMadrid());
        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
        when(textosService.texto(anyString(), any(Usuario.class), any())).thenReturn("texto");
        when(textosService.texto(anyString(), any(Usuario.class))).thenReturn("texto");
        when(notificacionService.enviarNotificacion(eq("token-fcm"), anyString(), anyString())).thenReturn(true);

        notificacionScheduler.enviarRecordatorios();

        verify(dispositivoService).olvidarToken(1, AppNorday.HABITOS, "token-fcm");
    }
}
