package com.joaquim.habitosapp;

import com.joaquim.habitosapp.model.*;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import com.joaquim.habitosapp.service.MotorLogrosService;
import com.joaquim.habitosapp.service.RegistroService;
import com.joaquim.habitosapp.service.UsuarioMonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RegistroServiceTest {

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @Mock
    private MotorLogrosService motorLogrosService;

    @Mock
    private UsuarioMonedaService usuarioMonedaService;

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

        racha = new Racha(habito);
    }

    @Test
    void alCompletarHabitoDiarioMeta1_laRachaSubeAUno() {
        // Arrange: no hay registros previos hoy, la racha existe y empieza en 0
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        // Act
        registroService.completarHabito(habito, "");

        // Assert: la racha subió a 1 y el flag quedó marcado
        assertEquals(1, racha.getRachaActual());
        assertTrue(racha.isMetaAlcanzadaPeriodoActual());
    }


    @Test
    void alCompletarHabitoDiarioMetaMultiple_laRachaNoSubeHastaAlcanzarLaMeta() {
        // Arrange: hábito diario con meta 3, ya hay 1 completado antes (en el rango de hoy)
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, ""));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        // Act: completamos una 2ª vez (total 2 de 3, aún no llega a la meta)
        registroService.completarHabito(habito, "");

        // Assert: la racha sigue en 0, el flag sigue en false
        assertEquals(0, racha.getRachaActual());
        assertFalse(racha.isMetaAlcanzadaPeriodoActual());
    }

    @Test
    void alCompletarHabitoDiarioMetaMultiple_laRachaSubeAlAlcanzarLaMeta() {
        // Arrange: hábito diario con meta 3, ya hay 2 completados antes
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(
                new Registro(habito, true, ""),
                new Registro(habito, true, "")
        );

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        // Act: completamos la 3ª vez, alcanzando la meta exacta
        registroService.completarHabito(habito, "");

        // Assert: la racha sube a 1 y el flag queda marcado
        assertEquals(1, racha.getRachaActual());
        assertTrue(racha.isMetaAlcanzadaPeriodoActual());
    }

    @Test
    void alCompletarMasVecesQueLaMeta_noSeOtorganPuntosExtra() {
        // Arrange: hábito diario con meta 1, YA completado hoy (1 registro previo)
        habito.setMeta(1);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, ""));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        // Act: completamos una 2ª vez, superando la meta
        registroService.completarHabito(habito, "");

        // Assert: NUNCA se llama a registrarMovimiento con origen HABITO_COMPLETADO
        verify(usuarioMonedaService, never()).registrarMovimiento(
                eq(usuario), anyInt(), eq("HABITO_COMPLETADO"), anyInt(), anyString()
        );
    }

    @Test
    void alCompletarHabitoSemanal_laRachaNoSubeSiNoSeAlcanzaLaMeta() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, ""));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        assertEquals(0, racha.getRachaActual());
        assertFalse(racha.isMetaAlcanzadaPeriodoActual());
    }

    @Test
    void alCompletarHabitoSemanal_laRachaSubeAlAlcanzarLaMetaAMitadDeSemana() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(
                new Registro(habito, true, ""),
                new Registro(habito, true, "")
        );

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        assertEquals(1, racha.getRachaActual());
        assertTrue(racha.isMetaAlcanzadaPeriodoActual());
    }

    @Test
    void alCompletarDosVecesTrasAlcanzarLaMeta_laRachaNoSubeDeNuevo() {
        habito.setMeta(3);
        racha.setRachaActual(1);
        racha.setMetaAlcanzadaPeriodoActual(true); // ya se alcanzó este periodo

        List<Registro> registrosPrevios = List.of(
                new Registro(habito, true, ""),
                new Registro(habito, true, ""),
                new Registro(habito, true, "")
        );

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        assertEquals(1, racha.getRachaActual()); // no subió de nuevo
    }

    @Test
    void alAlcanzarLaMeta_seOtorganPuntosPorHitoDeRacha() {
        habito.setMeta(1);
        racha.setRachaActual(2); // al subir a 3, debe activar el hito de puntos
        racha.setRachaMaxima(2);

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        assertEquals(3, racha.getRachaActual());
        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(20), eq("HITO_RACHA"), eq(habito.getHabitoId()), anyString()
        );
    }

    @Test
    void alNoAlcanzarLaMetaAun_noSeOtorganPuntosPorHitoDeRacha() {
        habito.setMeta(3);
        List<Registro> registrosPrevios = List.of(new Registro(habito, true, ""));

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(registrosPrevios);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        verify(usuarioMonedaService, never()).registrarMovimiento(
                eq(usuario), anyInt(), eq("HITO_RACHA"), anyInt(), anyString()
        );
    }


}