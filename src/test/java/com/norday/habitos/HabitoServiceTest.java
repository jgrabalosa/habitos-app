package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.HabitoService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.service.LogrosHabitosService;
import com.norday.habitos.service.RachaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.ZoneId;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class HabitoServiceTest {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.now(ZONA);

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private LogrosHabitosService logrosHabitosService;

    @Mock
    private RachaService rachaService;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private HabitoService habitoService;

    private Usuario usuario;
    private Habito existente;
    private Racha racha;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);

        existente = new Habito();
        existente.setHabitoId(20);
        existente.setNombre("Gym");
        existente.setFrecuencia(Frecuencia.DIARIO);
        existente.setMeta(1);
        existente.setPropietario(usuario);

        racha = new Racha(existente, HOY);
        racha.setRachaActual(5);
        racha.setRachaMaxima(5);
        racha.setPeriodoMetaAlcanzada(existente.getFrecuencia().rangoPeriodoActual(ZONA)[0]);
    }

    @Test
    void alCambiarLaFrecuencia_laRachaSeReseteaACero() {
        // Arrange: llega un hábito con la MISMA id pero frecuencia distinta (SEMANAL)
        Habito habitoEditado = new Habito();
        habitoEditado.setHabitoId(20);
        habitoEditado.setNombre("Gym");
        habitoEditado.setFrecuencia(Frecuencia.SEMANAL);
        habitoEditado.setMeta(3);
        habitoEditado.setPropietario(usuario);

        when(habitoDAO.findById(20)).thenReturn(existente);
        when(rachaDAO.findByHabito(habitoEditado)).thenReturn(racha);

        // Act
        habitoService.actualizar(habitoEditado);

        // Assert: la racha se resetea, pero la racha máxima NO se toca
        assertEquals(0, racha.getRachaActual());
        assertNull(racha.getPeriodoMetaAlcanzada());
        assertEquals(5, racha.getRachaMaxima());
    }

    @Test
    void alEditarSinCambiarLaFrecuencia_laRachaNoSeToca() {
        // Arrange: llega un hábito con la MISMA frecuencia (DIARIO), solo cambia el nombre
        Habito habitoEditado = new Habito();
        habitoEditado.setHabitoId(20);
        habitoEditado.setNombre("Gym renombrado");
        habitoEditado.setFrecuencia(Frecuencia.DIARIO);
        habitoEditado.setMeta(1);
        habitoEditado.setPropietario(usuario);

        when(habitoDAO.findById(20)).thenReturn(existente);

        // Act
        habitoService.actualizar(habitoEditado);

        // Assert: nunca se llama a rachaDAO.findByHabito ni a update sobre la racha
        verify(rachaDAO, never()).findByHabito(any());
        assertEquals(5, racha.getRachaActual()); // sigue como estaba
    }
}