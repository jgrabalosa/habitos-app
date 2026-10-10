package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.ParametrosRitmo;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import com.norday.habitos.service.RitmoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Semana de referencia: del lunes 5-oct-2026 al domingo 11-oct-2026. */
@ExtendWith(MockitoExtension.class)
class RitmoServiceTest {

    private static final double D = 0.000001;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Mock
    private IRitmoDAO ritmoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private RitmoService ritmoService;

    private Usuario usuario;
    private Habito habito;

    private static LocalDate d(int mes, int dia) {
        return LocalDate.of(2026, mes, dia);
    }

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setZonaHoraria("Europe/Madrid");

        habito = new Habito();
        habito.setHabitoId(10);
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
    }

    private Ritmo anclaEn(double valor, LocalDate desde) {
        Ritmo ritmo = new Ritmo(habito, desde);
        ritmo.setValor(valor);
        when(ritmoDAO.findByHabito(habito)).thenReturn(ritmo);
        return ritmo;
    }

    private Registro hecho(LocalDate fecha) {
        return new Registro(habito, true, "", fecha);
    }

    @Test
    void leeLosRegistrosDesdeElAnclaHastaAyer_yNoGuardaSiElAnclaNoAvanza() {
        anclaEn(0.5, d(10, 5));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 7)))
                .thenReturn(List.of(hecho(d(10, 5)), hecho(d(10, 6)), hecho(d(10, 7))));

        double ritmo = ritmoService.ritmoVigente(habito, d(10, 8));

        assertEquals(0.628501, ritmo, D);
        verify(ritmoDAO, never()).update(any());
        verify(ritmoDAO, never()).save(any());
    }

    @Test
    void alEmpezarUnaSemanaNueva_avanzaElAnclaYLaGuarda() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(5);
        anclaEn(0.5, d(10, 5));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 11)))
                .thenReturn(List.of(hecho(d(10, 5)), hecho(d(10, 6)), hecho(d(10, 7))));

        double ritmo = ritmoService.ritmoVigente(habito, d(10, 12));

        assertEquals(0.55, ritmo, D);
        ArgumentCaptor<Ritmo> guardado = ArgumentCaptor.forClass(Ritmo.class);
        verify(ritmoDAO).update(guardado.capture());
        assertEquals(0.55, guardado.getValue().getValor(), D);
        assertEquals(d(10, 12), guardado.getValue().getDesde());
        assertSame(habito, guardado.getValue().getHabito());
    }

    @Test
    void unSemanalConElAnclaAMitadDeSemana_leeDesdeElLunes() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(3);
        anclaEn(0.5, d(10, 10));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 11)))
                .thenReturn(List.of(hecho(d(10, 6)), hecho(d(10, 7)), hecho(d(10, 10))));

        double ritmo = ritmoService.ritmoVigente(habito, d(10, 12));

        assertEquals(0.75, ritmo, D);
    }

    @Test
    void losRegistrosSinCompletarNoCuentan() {
        anclaEn(0.5, d(10, 5));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 5)))
                .thenReturn(List.of(new Registro(habito, false, "", d(10, 5))));

        double ritmo = ritmoService.ritmoVigente(habito, d(10, 6));

        assertEquals(0.483766, ritmo, D);
    }

    @Test
    void siNoPuedeHaberCerradoNada_noConsultaRegistros() {
        anclaEn(0.5, d(10, 8));

        double ritmo = ritmoService.ritmoVigente(habito, d(10, 8));

        assertEquals(0.5, ritmo, D);
        verifyNoInteractions(registroDAO);
        verify(ritmoDAO, never()).update(any());
    }

    @Test
    void unHabitoSinFilaDeRitmo_empiezaHoyEnElValorDePartida() {
        when(ritmoDAO.findByHabito(habito)).thenReturn(null);

        double ritmo = ritmoService.ritmoVigente(habito, d(10, 8));

        assertEquals(ParametrosRitmo.VALOR_INICIAL, ritmo, D);
        ArgumentCaptor<Ritmo> creado = ArgumentCaptor.forClass(Ritmo.class);
        verify(ritmoDAO).save(creado.capture());
        assertSame(habito, creado.getValue().getHabito());
        assertEquals(ParametrosRitmo.VALOR_INICIAL, creado.getValue().getValor(), D);
        assertEquals(d(10, 8), creado.getValue().getDesde());
        verifyNoInteractions(registroDAO);
    }

    @Test
    void sinFecha_usaElHoyDeLaZonaDelUsuario() {
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(MADRID);
        anclaEn(0.5, LocalDate.now(MADRID));

        double ritmo = ritmoService.ritmoVigente(habito);

        assertEquals(0.5, ritmo, D);
        verify(zonaUsuarioService).zonaDe(usuario);
        verifyNoInteractions(registroDAO);
    }
}
