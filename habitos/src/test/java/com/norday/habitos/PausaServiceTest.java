package com.norday.habitos;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.habitos.config.ProductosHabitosInitializer;
import com.norday.habitos.model.Habito;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.ObjetivoService;
import com.norday.habitos.service.PausaService;
import com.norday.habitos.service.RitmoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Lo que los tests de integración de pausa no ven: el orden de las llamadas
 * y el «todo o nada». Aquí todo va con mocks.
 */
@ExtendWith(MockitoExtension.class)
class PausaServiceTest {

    // Con 25 horas entre una y otra, el «hoy» de la máquina no puede coincidir con las dos.
    private static final String KIRITIMATI = "Pacific/Kiritimati";
    private static final String PAGO_PAGO = "Pacific/Pago_Pago";
    private static final LocalDate HOY = LocalDate.of(2026, 11, 19);

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private RitmoService ritmoService;

    @Mock
    private ProductoService productoService;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @Mock
    private ObjetivoService objetivoService;

    @Mock
    private HabitoService habitoService;

    @InjectMocks
    private PausaService pausaService;

    private Usuario propietario;

    @BeforeEach
    void preparar() {
        propietario = new Usuario();
    }

    private Habito habito(boolean activo) {
        Habito habito = new Habito();
        habito.setPropietario(propietario);
        habito.setActivo(activo);
        return habito;
    }

    private void zona(String id) {
        when(zonaUsuarioService.zonaDe(propietario)).thenReturn(ZoneId.of(id));
    }

    // ---------- pausar ----------

    @Test
    void pausar_idInexistente_lanzaYNoBloqueaNiGastaNiTocaElRitmo() {
        when(habitoDAO.findById(7)).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class, () -> pausaService.pausar(7));

        verifyNoInteractions(habitoService, productoService, ritmoService, objetivoService);
        verify(habitoDAO, never()).update(any());
    }

    @Test
    void pausar_trabajaConElHabitoReleido_siYaEstaEnPausaConflictoYNoGasta() {
        Habito leido = habito(true);
        Habito releido = habito(false);
        when(habitoDAO.findById(7)).thenReturn(leido);
        when(habitoService.bloquear(leido)).thenReturn(releido);

        assertThrows(ConflictoException.class, () -> pausaService.pausar(7));

        verify(productoService, never()).gastarUnidad(any(), any());
        verifyNoInteractions(ritmoService, objetivoService);
        verify(habitoDAO, never()).update(any());
    }

    @Test
    void pausar_sinUnidades_laExcepcionSaleYNoSeTocaNada() {
        Habito habito = habito(true);
        when(habitoDAO.findById(7)).thenReturn(habito);
        when(habitoService.bloquear(habito)).thenReturn(habito);
        doThrow(new ConflictoException("Sin unidades"))
                .when(productoService).gastarUnidad(propietario, ProductosHabitosInitializer.PAUSA);

        assertThrows(ConflictoException.class, () -> pausaService.pausar(7));

        verifyNoInteractions(objetivoService, ritmoService);
        verify(habitoDAO, never()).update(any());
        assertTrue(habito.isActivo());
    }

    @ParameterizedTest
    @ValueSource(strings = {KIRITIMATI, PAGO_PAGO})
    void pausar_correcto_ordenYHoyEnLaZonaDelPropietario(String zona) {
        Habito leido = habito(true);
        Habito releido = habito(true);
        when(habitoDAO.findById(7)).thenReturn(leido);
        when(habitoService.bloquear(leido)).thenReturn(releido);
        zona(zona);
        AtomicBoolean activoAlAnclar = new AtomicBoolean(false);
        doAnswer(invocacion -> {
            activoAlAnclar.set(((Habito) invocacion.getArgument(0)).isActivo());
            return null;
        }).when(ritmoService).anclarAntesDeCambiar(releido);

        pausaService.pausar(7);

        LocalDate hoy = LocalDate.now(ZoneId.of(zona));
        InOrder orden = inOrder(habitoService, productoService, objetivoService, ritmoService, habitoDAO);
        orden.verify(habitoService).bloquear(leido);
        orden.verify(productoService).gastarUnidad(propietario, ProductosHabitosInitializer.PAUSA);
        orden.verify(objetivoService).ponerAlDia(List.of(releido), hoy);
        orden.verify(ritmoService).anclarAntesDeCambiar(releido);
        ArgumentCaptor<Habito> guardado = ArgumentCaptor.forClass(Habito.class);
        orden.verify(habitoDAO).update(guardado.capture());
        assertSame(releido, guardado.getValue());
        assertFalse(guardado.getValue().isActivo());
        assertTrue(activoAlAnclar.get(), "El ritmo se ancla con el hábito aún activo");
    }

    // ---------- reanudar ----------

    @Test
    void reanudarPorId_idInexistente_lanza() {
        when(habitoDAO.findById(7)).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class, () -> pausaService.reanudar(7));

        verifyNoInteractions(habitoService, ritmoService, objetivoService);
        verify(habitoDAO, never()).update(any());
    }

    @ParameterizedTest
    @ValueSource(strings = {KIRITIMATI, PAGO_PAGO})
    void reanudarPorId_usaElHoyDeLaZonaDelPropietario(String zona) {
        Habito habito = habito(false);
        when(habitoDAO.findById(7)).thenReturn(habito);
        when(habitoService.bloquear(habito)).thenReturn(habito);
        zona(zona);

        pausaService.reanudar(7);

        LocalDate hoy = LocalDate.now(ZoneId.of(zona));
        verify(objetivoService).ponerAlDia(List.of(habito), hoy);
        verify(ritmoService).reanudar(habito, hoy);
    }

    @Test
    void reanudar_siElReleidoYaEstaActivo_noHaceNada() {
        Habito leido = habito(false);
        Habito releido = habito(true);
        when(habitoService.bloquear(leido)).thenReturn(releido);

        pausaService.reanudar(leido, HOY);

        verifyNoInteractions(objetivoService, ritmoService);
        verify(habitoDAO, never()).update(any());
    }

    @Test
    void reanudar_enPausa_ordenYActivoAlGuardar() {
        Habito leido = habito(false);
        Habito releido = habito(false);
        when(habitoService.bloquear(leido)).thenReturn(releido);

        pausaService.reanudar(leido, HOY);

        InOrder orden = inOrder(habitoService, objetivoService, ritmoService, habitoDAO);
        orden.verify(habitoService).bloquear(leido);
        orden.verify(objetivoService).ponerAlDia(List.of(releido), HOY);
        orden.verify(ritmoService).reanudar(releido, HOY);
        ArgumentCaptor<Habito> guardado = ArgumentCaptor.forClass(Habito.class);
        orden.verify(habitoDAO).update(guardado.capture());
        assertSame(releido, guardado.getValue());
        assertTrue(guardado.getValue().isActivo());
    }
}
