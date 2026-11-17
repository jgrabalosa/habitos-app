package com.norday.habitos;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.TextosService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.service.ObjetivoService;
import com.norday.habitos.service.RitmoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Guardar un cambio de objetivo cuando otra petición acaba de guardar otro
 * para el mismo hábito y la misma fecha: conflicto (409), no un 500. Y poner
 * al día la copia con la unidad y los minutos.
 */
@ExtendWith(MockitoExtension.class)
class ObjetivoServiceTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABADO = VIERNES.plusDays(1);

    @Mock private IHabitoObjetivoDAO objetivoDAO;
    @Mock private IHabitoDAO habitoDAO;
    @Mock private RitmoService ritmoService;
    @Mock private TextosService textosService;

    @InjectMocks
    private ObjetivoService objetivoService;

    private Habito habito() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1, usuario, null, INICIO);
        habito.setHabitoId(10);
        habito.setActivo(true);
        habito.setUnidad(Unidad.MINUTOS);
        habito.setMinutosObjetivo(30);
        return habito;
    }

    private static Objetivo minutos(int m) {
        return new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, m, null);
    }

    @Test
    void siOtraPeticionAcabaDeGuardarLaMismaFecha_esUnConflicto() {
        Habito habito = habito();
        when(objetivoDAO.findByHabito(habito)).thenReturn(List.of(new HabitoObjetivo(habito, INICIO, minutos(30))));
        when(objetivoDAO.insertarSiLibre(any())).thenReturn(false);
        when(textosService.texto("habito.objetivo.a.la.vez", habito.getPropietario())).thenReturn("a la vez");

        ConflictoException e = assertThrows(ConflictoException.class,
                () -> objetivoService.programar(habito, minutos(40), SABADO, VIERNES));
        assertEquals("a la vez", e.getMessage());
    }

    @Test
    void sinCarrera_laVersionNuevaSeGuardaConSuUnidadYSusMinutos() {
        Habito habito = habito();
        when(objetivoDAO.findByHabito(habito)).thenReturn(List.of(new HabitoObjetivo(habito, INICIO, minutos(30))));
        when(objetivoDAO.insertarSiLibre(any())).thenReturn(true);

        objetivoService.programar(habito, minutos(40), SABADO, VIERNES);

        verify(objetivoDAO).insertarSiLibre(argThat(v -> v.getDesde().equals(SABADO)
                && v.getUnidad() == Unidad.MINUTOS && v.getMinutosObjetivo() == 40));
        verify(objetivoDAO, never()).save(any());
    }

    @Test
    void ponerAlDia_copiaLaUnidadYLosMinutos_yAnclaElRitmoSiCambianLosMinutos() {
        Habito habito = habito();
        HabitoObjetivo primera = new HabitoObjetivo(habito, INICIO, minutos(30));
        HabitoObjetivo programada = new HabitoObjetivo(habito, SABADO, minutos(40));
        when(objetivoDAO.findByHabitos(List.of(habito)))
                .thenReturn(Map.of(10, List.of(primera, programada)));

        when(habitoDAO.actualizarObjetivo(habito, minutos(40))).thenReturn(true);

        objetivoService.ponerAlDia(List.of(habito), SABADO);

        // Solo las columnas del objetivo, nunca el hábito entero; se ancla
        // si se escribió, y después se relee el hábito.
        org.mockito.InOrder orden = inOrder(habitoDAO, ritmoService);
        orden.verify(habitoDAO).actualizarObjetivo(habito, minutos(40));
        orden.verify(ritmoService).anclarAntesDeCambiar(any(Habito.class), eq(SABADO));
        orden.verify(habitoDAO).releer(habito, minutos(40));
        verify(habitoDAO, never()).update(any());
    }

    @Test
    void ponerAlDia_siOtroCambioElObjetivoEntretanto_noAnclaNada() {
        Habito habito = habito();
        HabitoObjetivo primera = new HabitoObjetivo(habito, INICIO, minutos(30));
        HabitoObjetivo programada = new HabitoObjetivo(habito, SABADO, minutos(40));
        when(objetivoDAO.findByHabitos(List.of(habito)))
                .thenReturn(Map.of(10, List.of(primera, programada)));
        when(habitoDAO.actualizarObjetivo(habito, minutos(40))).thenReturn(false);

        objetivoService.ponerAlDia(List.of(habito), SABADO);

        verify(ritmoService, never()).anclarAntesDeCambiar(any(Habito.class), any());
        verify(habitoDAO).releer(habito, minutos(40));
    }

    @Test
    void anularProgramados_borraLoQueEmpiezaDespuesDeHoy() {
        Habito habito = habito();
        objetivoService.anularProgramados(habito, VIERNES);
        verify(objetivoDAO).deletePosterioresA(habito, VIERNES);
    }
}
