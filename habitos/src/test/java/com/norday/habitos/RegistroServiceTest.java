package com.norday.habitos;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.LogrosHabitosService;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.now(ZONA);

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @Mock
    private LogrosHabitosService logrosHabitosService;

    @Mock
    private UsuarioMonedaService usuarioMonedaService;

    @Mock
    private MascotaService mascotaService;

    @Mock
    private RachaService rachaService;

    @Mock
    private HabitoService habitoService;

    @Mock
    private ILogroDAO logroDAO;

    @Mock
    private IReversionRegistroDAO reversionRegistroDAO;

    @InjectMocks
    private RegistroService registroService;

    private Usuario usuario;
    private Habito habito;
    private Racha racha;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);

        habito = new Habito();
        habito.setHabitoId(10);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);

        racha = new Racha(habito, HOY);

        lenient().when(rachaService.zonaDe(any(Habito.class))).thenReturn(ZONA);
        // La instantánea de deshacer lee el estado previo de la mascota antes
        // de crear el Registro: sin este stub, mascotaPrevia sale null.
        lenient().when(mascotaService.obtenerOCrear(anyInt())).thenReturn(new Mascota(usuario));
    }

    @Test
    void alCompletarHabitoDiarioMeta1_laRachaSubeAUno() {
        // Arrange: no hay registros previos hoy, la racha existe y empieza en 0
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        // Act
        registroService.completarHabito(habito, "");

        // Assert: la racha subió a 1 y el flag quedó marcado
        assertEquals(1, racha.getRachaActual());
        assertTrue(racha.metaAlcanzadaEnPeriodoActual(ZONA));
    }


    @Test
    void alCompletarHabitoDiarioMetaMultiple_laRachaNoSubeHastaAlcanzarLaMeta() {
        // Arrange: hábito diario con meta 3, ya hay 1 completado antes (en el rango de hoy)
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, "", HOY));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        // Act: completamos una 2ª vez (total 2 de 3, aún no llega a la meta)
        registroService.completarHabito(habito, "");

        // Assert: la racha sigue en 0, el flag sigue en false
        assertEquals(0, racha.getRachaActual());
        assertFalse(racha.metaAlcanzadaEnPeriodoActual(ZONA));
    }

    @Test
    void alCompletarHabitoDiarioMetaMultiple_laRachaSubeAlAlcanzarLaMeta() {
        // Arrange: hábito diario con meta 3, ya hay 2 completados antes
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(
                new Registro(habito, true, "", HOY),
                new Registro(habito, true, "", HOY)
        );

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        // Act: completamos la 3ª vez, alcanzando la meta exacta
        registroService.completarHabito(habito, "");

        // Assert: la racha sube a 1 y el flag queda marcado
        assertEquals(1, racha.getRachaActual());
        assertTrue(racha.metaAlcanzadaEnPeriodoActual(ZONA));
    }

    @Test
    void alCompletarMasVecesQueLaMeta_seRechazaConConflicto() {
        // Arrange: hábito diario con meta 1, YA completado hoy (1 registro previo)
        habito.setMeta(1);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, "", HOY));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);

        // Act & Assert: completar una 2ª vez, superando la meta, se rechaza —
        // es la guarda de idempotencia contra doble toque o reintento.
        assertThrows(ConflictoException.class, () -> registroService.completarHabito(habito, ""));

        // Y nunca se llega a otorgar puntos
        verify(usuarioMonedaService, never()).registrarMovimiento(
                eq(usuario), anyInt(), eq("HABITO_COMPLETADO"), anyInt(), anyString()
        );
    }

    @Test
    void alCompletarHabitoSemanal_laRachaNoSubeSiNoSeAlcanzaLaMeta() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, "", HOY));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        assertEquals(0, racha.getRachaActual());
        assertFalse(racha.metaAlcanzadaEnPeriodoActual(ZONA));
    }

    @Test
    void alCompletarHabitoSemanal_laRachaSubeAlAlcanzarLaMetaAMitadDeSemana() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(
                new Registro(habito, true, "", HOY),
                new Registro(habito, true, "", HOY)
        );

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        assertEquals(1, racha.getRachaActual());
        assertTrue(racha.metaAlcanzadaEnPeriodoActual(ZONA));
    }

    @Test
    void alCompletarTrasAlcanzarLaMeta_seRechazaConConflictoYLaRachaNoSubeDeNuevo() {
        habito.setMeta(3);
        racha.setRachaActual(1);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().rangoPeriodoActual(ZONA)[0]); // ya se alcanzó este periodo

        List<Registro> registrosPrevios = List.of(
                new Registro(habito, true, "", HOY),
                new Registro(habito, true, "", HOY),
                new Registro(habito, true, "", HOY)
        );

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);

        // Act & Assert: un 4º completado tras alcanzar la meta se rechaza —
        // ni siquiera llega a tocar la racha.
        assertThrows(ConflictoException.class, () -> registroService.completarHabito(habito, ""));

        assertEquals(1, racha.getRachaActual()); // no subió de nuevo
    }

    @Test
    void alAlcanzarLaMeta_seOtorganPuntosPorHitoDeRacha() {
        habito.setMeta(1);
        racha.setRachaActual(2); // al subir a 3, debe activar el hito de puntos
        racha.setRachaMaxima(2);
        // La racha viene viva: se cumplió en el periodo anterior (ayer)
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().inicioPeriodoAnterior(ZONA));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        assertEquals(3, racha.getRachaActual());
        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(50), eq("HITO_RACHA"), eq(habito.getHabitoId()), anyString()
        );
    }

    @Test
    void alNoAlcanzarLaMetaAun_noSeOtorganPuntosPorHitoDeRacha() {
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, "", HOY));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        verify(usuarioMonedaService, never()).registrarMovimiento(
                eq(usuario), anyInt(), eq("HITO_RACHA"), anyInt(), anyString()
        );
    }


}