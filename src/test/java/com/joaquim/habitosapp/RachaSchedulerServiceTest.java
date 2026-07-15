package com.joaquim.habitosapp;

import com.joaquim.habitosapp.model.*;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.service.RachaSchedulerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RachaSchedulerServiceTest {

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @InjectMocks
    private RachaSchedulerService rachaSchedulerService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
    }

    private Habito crearHabito(int id, Frecuencia frecuencia) {
        Habito h = new Habito();
        h.setHabitoId(id);
        h.setNombre("Test");
        h.setFrecuencia(frecuencia);
        h.setMeta(1);
        h.setPropietario(usuario);
        return h;
    }

    @Test
    void alCerrarPeriodoDiario_siNoSeAlcanzoLaMeta_laRachaSeRompe() {
        Habito habito = crearHabito(1, Frecuencia.DIARIO);
        Racha racha = new Racha(habito);
        racha.setRachaActual(5);
        racha.setRachaMaxima(5);
        racha.setMetaAlcanzadaPeriodoActual(false); // no cumplió el día

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);

        rachaSchedulerService.evaluarCierreDePeriodos();

        assertEquals(0, racha.getRachaActual());
        assertFalse(racha.isMetaAlcanzadaPeriodoActual());
    }

    @Test
    void alCerrarPeriodoDiario_siSeAlcanzoLaMeta_laRachaNoSeToca() {
        Habito habito = crearHabito(2, Frecuencia.DIARIO);
        Racha racha = new Racha(habito);
        racha.setRachaActual(5);
        racha.setRachaMaxima(5);
        racha.setMetaAlcanzadaPeriodoActual(true); // sí cumplió el día

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);

        rachaSchedulerService.evaluarCierreDePeriodos();

        assertEquals(5, racha.getRachaActual()); // no se rompe
        assertFalse(racha.isMetaAlcanzadaPeriodoActual()); // pero el flag sí se resetea para el próximo periodo
    }

    @Test
    void alCerrarPeriodoSemanal_soloSeEvaluaEnLunes() {
        Habito habito = crearHabito(3, Frecuencia.SEMANAL);
        Racha racha = new Racha(habito);
        racha.setRachaActual(3);
        racha.setMetaAlcanzadaPeriodoActual(false);

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habito));

        LocalDate miercoles = LocalDate.of(2026, 7, 15); // fecha calculada FUERA del mock

        // Simulamos que HOY es miércoles (no lunes) — el semanal no debe evaluarse
        try (MockedStatic<LocalDate> mocked = mockStatic(LocalDate.class, CALLS_REAL_METHODS)) {
            mocked.when(LocalDate::now).thenReturn(miercoles);

            rachaSchedulerService.evaluarCierreDePeriodos();
        }

        // Como no es lunes, nunca se debe llamar a rachaDAO.findByHabito para este hábito
        verify(rachaDAO, never()).findByHabito(habito);
        assertEquals(3, racha.getRachaActual()); // intacta
    }

    @Test
    void trasEvaluarUnPeriodo_elFlagMetaAlcanzadaSeReseteaAFalse() {
        // Un hábito que rompió y otro que se mantuvo, ambos deben quedar con el flag en false
        Habito habitoRoto = crearHabito(4, Frecuencia.DIARIO);
        Racha rachaRota = new Racha(habitoRoto);
        rachaRota.setRachaActual(2);
        rachaRota.setMetaAlcanzadaPeriodoActual(false);

        Habito habitoMantenido = crearHabito(5, Frecuencia.DIARIO);
        Racha rachaMantenida = new Racha(habitoMantenido);
        rachaMantenida.setRachaActual(7);
        rachaMantenida.setMetaAlcanzadaPeriodoActual(true);

        when(habitoDAO.findTodosActivos()).thenReturn(List.of(habitoRoto, habitoMantenido));
        when(rachaDAO.findByHabito(habitoRoto)).thenReturn(rachaRota);
        when(rachaDAO.findByHabito(habitoMantenido)).thenReturn(rachaMantenida);

        rachaSchedulerService.evaluarCierreDePeriodos();

        assertFalse(rachaRota.isMetaAlcanzadaPeriodoActual());
        assertFalse(rachaMantenida.isMetaAlcanzadaPeriodoActual());
        verify(rachaDAO, times(2)).update(any());
    }
}