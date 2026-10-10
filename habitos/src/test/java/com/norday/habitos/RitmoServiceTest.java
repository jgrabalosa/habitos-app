package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.ParametrosRitmo;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.Tendencia;
import com.norday.habitos.repository.IHabitoDAO;
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
import java.util.Map;

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
    private IHabitoDAO habitoDAO;

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

    private Habito otroHabito(LocalDate fechaInicio) {
        Habito otro = new Habito();
        otro.setHabitoId(11);
        otro.setFrecuencia(Frecuencia.DIARIO);
        otro.setMeta(1);
        otro.setPropietario(usuario);
        otro.setFechaInicio(fechaInicio);
        return otro;
    }

    private Ritmo ancla(Habito deQuien, double valor, LocalDate desde) {
        Ritmo ritmo = new Ritmo(deQuien, desde);
        ritmo.setValor(valor);
        return ritmo;
    }

    private Registro hechoPor(Habito quien, LocalDate fecha) {
        return new Registro(quien, true, "", fecha);
    }

    @Test
    void ritmosVigentes_leeLosRegistrosDeTodosEnUnaSolaConsulta() {
        Habito semanal = otroHabito(LocalDate.of(2026, 1, 1));
        semanal.setFrecuencia(Frecuencia.SEMANAL);
        semanal.setMeta(3);
        List<Habito> habitos = List.of(habito, semanal);
        // El diario tiene el ancla en el lunes 5; el semanal, una semana antes.
        when(ritmoDAO.findByHabitos(habitos)).thenReturn(
                List.of(ancla(habito, 0.5, d(10, 5)), ancla(semanal, 0.5, d(9, 28))));
        when(registroDAO.findByHabitosAndRango(habitos, d(9, 28), d(10, 7))).thenReturn(List.of(
                hechoPor(semanal, d(9, 29)), hechoPor(semanal, d(9, 30)),
                hechoPor(habito, d(10, 1)), // anterior al ancla del diario: no cuenta
                hechoPor(habito, d(10, 5)), hechoPor(habito, d(10, 6)), hechoPor(habito, d(10, 7))));

        Map<Integer, Double> ritmos = ritmoService.ritmosVigentes(habitos, d(10, 8));

        assertEquals(0.628501, ritmos.get(10), D);
        assertEquals(0.583333, ritmos.get(11), D);
        verify(registroDAO, never()).findByHabitoAndRango(any(), any(), any());
        verify(ritmoDAO, never()).findByHabito(any());
        // Solo avanza el ancla del semanal: su semana del 28-sep ya cerró.
        ArgumentCaptor<Ritmo> guardado = ArgumentCaptor.forClass(Ritmo.class);
        verify(ritmoDAO).update(guardado.capture());
        assertSame(semanal, guardado.getValue().getHabito());
        assertEquals(0.583333, guardado.getValue().getValor(), D);
        assertEquals(d(10, 5), guardado.getValue().getDesde());
    }

    @Test
    void ritmosVigentes_sinHabitos_noConsultaNada() {
        assertTrue(ritmoService.ritmosVigentes(List.of(), d(10, 8)).isEmpty());
        verifyNoInteractions(ritmoDAO, registroDAO);
    }

    @Test
    void ritmosVigentes_siNadaPuedeHaberCerrado_noConsultaRegistros() {
        Habito otro = otroHabito(LocalDate.of(2026, 1, 1));
        List<Habito> habitos = List.of(habito, otro);
        when(ritmoDAO.findByHabitos(habitos)).thenReturn(
                List.of(ancla(habito, 0.9, d(10, 8)), ancla(otro, 0.5, d(10, 8))));

        Map<Integer, Double> ritmos = ritmoService.ritmosVigentes(habitos, d(10, 8));

        assertEquals(0.9, ritmos.get(10), D);
        assertEquals(0.5, ritmos.get(11), D);
        verifyNoInteractions(registroDAO);
        verify(ritmoDAO, never()).update(any());
    }

    @Test
    void ritmosVigentes_unHabitoSinFilaDeRitmo_empiezaHoyEnElValorDePartida() {
        Habito sinFila = otroHabito(LocalDate.of(2026, 1, 1));
        List<Habito> habitos = List.of(habito, sinFila);
        when(ritmoDAO.findByHabitos(habitos)).thenReturn(List.of(ancla(habito, 0.9, d(10, 8))));

        Map<Integer, Double> ritmos = ritmoService.ritmosVigentes(habitos, d(10, 8));

        assertEquals(0.9, ritmos.get(10), D);
        assertEquals(ParametrosRitmo.VALOR_INICIAL, ritmos.get(11), D);
        ArgumentCaptor<Ritmo> creado = ArgumentCaptor.forClass(Ritmo.class);
        verify(ritmoDAO).save(creado.capture());
        assertSame(sinFila, creado.getValue().getHabito());
        assertEquals(d(10, 8), creado.getValue().getDesde());
    }

    @Test
    void ritmoGeneral_esLaMediaDeLosHabitosActivos() {
        Habito otro = otroHabito(LocalDate.of(2026, 1, 1));
        List<Habito> activos = List.of(habito, otro);
        when(habitoDAO.findActivos(usuario)).thenReturn(activos);
        when(ritmoDAO.findByHabitos(activos)).thenReturn(
                List.of(ancla(habito, 0.9, d(10, 8)), ancla(otro, 0.5, d(10, 8))));

        double general = ritmoService.ritmoGeneral(usuario, d(10, 8)).orElseThrow();

        assertEquals(0.7, general, D);
    }

    @Test
    void ritmoGeneral_unHabitoCreadoHoyNoLoBaja() {
        Habito creadoHoy = otroHabito(d(10, 8));
        List<Habito> activos = List.of(habito, creadoHoy);
        when(habitoDAO.findActivos(usuario)).thenReturn(activos);
        when(ritmoDAO.findByHabitos(activos)).thenReturn(
                List.of(ancla(habito, 0.9, d(10, 8)), ancla(creadoHoy, 0.5, d(10, 8))));

        double general = ritmoService.ritmoGeneral(usuario, d(10, 8)).orElseThrow();

        assertEquals(0.9, general, D);
    }

    @Test
    void ritmoGeneral_alBorrarUnHabitoSeRecalcula() {
        Habito otro = otroHabito(LocalDate.of(2026, 1, 1));
        List<Habito> losDos = List.of(habito, otro);
        List<Habito> soloUno = List.of(habito);
        when(habitoDAO.findActivos(usuario)).thenReturn(losDos, soloUno);
        when(ritmoDAO.findByHabitos(losDos)).thenReturn(
                List.of(ancla(habito, 0.9, d(10, 8)), ancla(otro, 0.5, d(10, 8))));
        when(ritmoDAO.findByHabitos(soloUno)).thenReturn(List.of(ancla(habito, 0.9, d(10, 8))));

        double conLosDos = ritmoService.ritmoGeneral(usuario, d(10, 8)).orElseThrow();
        double trasBorrarUno = ritmoService.ritmoGeneral(usuario, d(10, 8)).orElseThrow();

        assertEquals(0.7, conLosDos, D);
        assertEquals(0.9, trasBorrarUno, D);
    }

    @Test
    void ritmoGeneral_sinHabitosActivos_noHayRitmoGeneral() {
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());

        assertTrue(ritmoService.ritmoGeneral(usuario, d(10, 8)).isEmpty());
        verifyNoInteractions(ritmoDAO);
    }

    /** Los ocho días del lunes 5 al lunes 12, todos hechos. */
    private List<Registro> delCincoAlDoce(Habito quien) {
        List<Registro> registros = new java.util.ArrayList<>();
        for (int dia = 5; dia <= 12; dia++) {
            registros.add(hechoPor(quien, d(10, dia)));
        }
        return registros;
    }

    @Test
    void ritmoLeido_sinAnclaAnterior_comparaConLaQueHay() {
        anclaEn(0.5, d(10, 5));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 7)))
                .thenReturn(List.of(hecho(d(10, 5)), hecho(d(10, 6)), hecho(d(10, 7))));

        RitmoLeido leido = ritmoService.ritmoLeido(habito, d(10, 8));

        assertEquals(0.628501, leido.valor(), D);
        assertEquals(0.5, leido.haceUnaSemana(), D);
        assertEquals(Tendencia.SUBE, leido.tendencia());
    }

    @Test
    void ritmoLeido_alAvanzarElAncla_guardaLaQueDejaAtras() {
        anclaEn(0.5, d(10, 5));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 12)))
                .thenReturn(delCincoAlDoce(habito));

        // Martes 13: el ancla pasa del lunes 5 al lunes 12.
        RitmoLeido leido = ritmoService.ritmoLeido(habito, d(10, 13));

        assertEquals(0.773569, leido.valor(), D);
        // Hace siete días era martes 6: solo había cerrado el lunes 5.
        assertEquals(0.547138, leido.haceUnaSemana(), D);
        assertEquals(Tendencia.SUBE, leido.tendencia());
        ArgumentCaptor<Ritmo> guardado = ArgumentCaptor.forClass(Ritmo.class);
        verify(ritmoDAO).update(guardado.capture());
        assertEquals(0.75, guardado.getValue().getValor(), D);
        assertEquals(d(10, 12), guardado.getValue().getDesde());
        assertEquals(0.5, guardado.getValue().getValorPrevio(), D);
        assertEquals(d(10, 5), guardado.getValue().getDesdePrevio());
    }

    @Test
    void ritmoLeido_conAnclaAnterior_leeLosRegistrosDesdeElla() {
        Ritmo ritmo = anclaEn(0.75, d(10, 12));
        ritmo.setValorPrevio(0.5);
        ritmo.setDesdePrevio(d(10, 5));
        when(registroDAO.findByHabitoAndRango(habito, d(10, 5), d(10, 13)))
                .thenReturn(delCincoAlDoce(habito));

        // Miércoles 14: el lunes 12 hecho, el martes 13 sin nada.
        RitmoLeido leido = ritmoService.ritmoLeido(habito, d(10, 14));

        assertEquals(0.748453, leido.valor(), D);
        // Hace siete días era miércoles 7: habían cerrado el lunes 5 y el martes 6.
        assertEquals(0.589832, leido.haceUnaSemana(), D);
        assertEquals(Tendencia.SUBE, leido.tendencia());
        verify(ritmoDAO, never()).update(any());
    }

    @Test
    void ritmosLeidos_leeDesdeElAnclaAnteriorMasAntigua() {
        Habito otro = otroHabito(LocalDate.of(2026, 1, 1));
        List<Habito> habitos = List.of(habito, otro);
        Ritmo conAnterior = ancla(habito, 0.75, d(10, 12));
        conAnterior.setValorPrevio(0.5);
        conAnterior.setDesdePrevio(d(10, 5));
        when(ritmoDAO.findByHabitos(habitos)).thenReturn(
                List.of(conAnterior, ancla(otro, 0.5, d(10, 12))));
        when(registroDAO.findByHabitosAndRango(habitos, d(10, 5), d(10, 13)))
                .thenReturn(delCincoAlDoce(habito));

        Map<Integer, RitmoLeido> leidos = ritmoService.ritmosLeidos(habitos, d(10, 14));

        assertEquals(0.748453, leidos.get(10).valor(), D);
        assertEquals(0.589832, leidos.get(10).haceUnaSemana(), D);
        assertEquals(Tendencia.SUBE, leidos.get(10).tendencia());
        // El otro no ha hecho nada el lunes ni el martes: baja 3 puntos, que
        // todavía no es "baja".
        assertEquals(0.468059, leidos.get(11).valor(), D);
        assertEquals(0.5, leidos.get(11).haceUnaSemana(), D);
        assertEquals(Tendencia.IGUAL, leidos.get(11).tendencia());
        verify(ritmoDAO, never()).update(any());
    }
}
