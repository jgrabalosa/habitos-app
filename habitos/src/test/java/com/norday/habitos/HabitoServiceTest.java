package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
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
import java.util.ArrayList;
import java.util.List;
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

    // ── esDiaCompleto ────────────────────────────────────────────────────
    // Solo cuentan los DIARIO: "hecho" = completadosPeriodo >= meta. Los
    // SEMANAL se ignoran, porque no tienen un dia en el que toquen y uno a
    // medias bloqueaba el animo de la mascota los siete dias de la semana.

    private Habito habito(int id, Frecuencia frecuencia, int meta) {
        Habito h = new Habito();
        h.setHabitoId(id);
        h.setNombre("H" + id);
        h.setFrecuencia(frecuencia);
        h.setMeta(meta);
        h.setPropietario(usuario);
        return h;
    }

    /** Deja al habito con `completados` registros dentro de su periodo actual. */
    private void conCompletados(Habito h, int completados) {
        LocalDate[] periodo = h.getFrecuencia().rangoPeriodoActual(ZONA);
        List<Registro> registros = new ArrayList<>();
        for (int i = 0; i < completados; i++) {
            registros.add(new Registro(h, true, null, periodo[0]));
        }
        when(registroDAO.findByHabitoAndRango(h, periodo[0], periodo[1])).thenReturn(registros);
    }

    @Test
    void sinHabitosActivosNoHayDiaCompleto_noHabiaNadaQueCumplir() {
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());

        assertFalse(habitoService.esDiaCompleto(usuario));
    }

    @Test
    void conTodosLosHabitosDiariosEnSuMetaElDiaEstaCompleto() {
        Habito madrugar = habito(1, Frecuencia.DIARIO, 2);
        Habito leer = habito(2, Frecuencia.DIARIO, 1);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(madrugar, leer));
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(ZONA);
        conCompletados(madrugar, 2);
        conCompletados(leer, 1);

        assertTrue(habitoService.esDiaCompleto(usuario));
    }

    @Test
    void unSemanalAMediasNoBloqueaElDia_siLosDiariosEstanHechos() {
        Habito diario = habito(1, Frecuencia.DIARIO, 2);
        Habito semanal = habito(2, Frecuencia.SEMANAL, 3); // va 1 de 3 esta semana
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(diario, semanal));
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(ZONA);
        conCompletados(diario, 2);

        assertTrue(habitoService.esDiaCompleto(usuario));
        // Ni siquiera se le pregunta al semanal: no entra en el calculo.
        verify(registroDAO, never()).findByHabitoAndRango(eq(semanal), any(), any());
    }

    @Test
    void soloConSemanales_elDiaEstaCompleto_hoyNoHabiaNadaQueCumplir() {
        Habito semanal = habito(2, Frecuencia.SEMANAL, 3);
        Habito otroSemanal = habito(3, Frecuencia.SEMANAL, 1);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(semanal, otroSemanal));
        // Sin ninguno completado y sin mirar registros: la lista de diarios
        // queda vacia y eso ya es dia cumplido.

        assertTrue(habitoService.esDiaCompleto(usuario));
    }

    @Test
    void siUnHabitoDiarioSeQuedaCortoElDiaNoEstaCompleto() {
        Habito diario = habito(1, Frecuencia.DIARIO, 2);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(diario));
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(ZONA);
        conCompletados(diario, 1); // le falta uno

        assertFalse(habitoService.esDiaCompleto(usuario));
    }

    @Test
    void pasarseDeLaMetaSigueContandoComoHecho() {
        Habito diario = habito(1, Frecuencia.DIARIO, 2);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(diario));
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(ZONA);
        conCompletados(diario, 5);

        assertTrue(habitoService.esDiaCompleto(usuario));
    }
}
