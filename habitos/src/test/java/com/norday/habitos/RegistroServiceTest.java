package com.norday.habitos;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.ReversionRegistro;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.LogrosHabitosService;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.ArrayList;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.DayOfWeek;
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
    private IUsuarioLogroDAO usuarioLogroDAO;

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
        habito.setActivo(true);
        habito.setHabitoId(10);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);

        racha = new Racha(habito, HOY);

        lenient().when(rachaService.zonaDe(any(Habito.class))).thenReturn(ZONA);
        lenient().when(rachaService.hoyDe(any(Habito.class))).thenReturn(HOY);
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
    void alCompletarUnaFechaPasada_seGuardaLaFechaYActualizaSuPeriodo() {
        // Día fijo a propósito: con el límite de la semana en curso, un
        // miércoles siempre tiene un martes válido detrás. Contra el reloj
        // real este test sería imposible los lunes.
        LocalDate miercoles = LocalDate.of(2026, 9, 9);
        LocalDate martes = miercoles.minusDays(1);
        when(rachaService.hoyDe(any(Habito.class))).thenReturn(miercoles);

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(registroDAO.findByHabito(habito))
                .thenReturn(List.of(new Registro(habito, true, "", martes)));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "", martes);

        ArgumentCaptor<Registro> capturado = ArgumentCaptor.forClass(Registro.class);
        verify(registroDAO).save(capturado.capture());
        assertEquals(martes, capturado.getValue().getFecha());
        assertEquals(1, racha.getRachaActual());
        assertEquals(habito.getFrecuencia().rangoPeriodo(martes)[0],
                racha.getPeriodoMetaAlcanzada());
    }

    @Test
    void alCompletarUnaFechaFutura_seRechaza() {
        assertThrows(ConflictoException.class,
                () -> registroService.completarHabito(habito, "", HOY.plusDays(1)));
        verify(registroDAO, never()).save(any(Registro.class));
    }

    @Test
    void alCompletarUnaFechaAnteriorAlLunes_seRechaza() {
        LocalDate lunes = HOY.with(DayOfWeek.MONDAY);
        LocalDate fechaAnterior = lunes.minusDays(1);

        assertThrows(ConflictoException.class,
                () -> registroService.completarHabito(habito, "", fechaAnterior));
        verify(registroDAO, never()).save(any(Registro.class));
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
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

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
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

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

    @Disabled("B2: los hitos ya no cuelgan de la racha, van por días hechos. Lo sustituye alLlegarATresDiasHechos_seCobraElHitoAunqueNoSeanSeguidos")
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
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        verify(usuarioMonedaService, never()).registrarMovimiento(
                eq(usuario), anyInt(), eq("HITO_RACHA"), anyInt(), anyString()
        );
    }

    @Test
    void alCompletarHoyConLaRachaMuerta_laInstantaneaGuardaCero() {
        // Racha de 6 abandonada hace dos semanas: al completar hoy no
        // continúa desde 6, vuelve a empezar en 1.
        racha.setRachaActual(6);
        racha.setUltimaFecha(HOY.minusDays(14));
        racha.setPeriodoMetaAlcanzada(HOY.minusDays(14));

        // rachaActualVigente es quien materializa la rotura. Aquí se emula
        // su contrato, que está probado de verdad en RachaServiceTest.
        doAnswer(invocacion -> {
            racha.setRachaActual(0);
            return 0;
        }).when(rachaService).rachaActualVigente(racha);

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        assertEquals(1, racha.getRachaActual());

        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(0, captor.getValue().getRachaActualPrevia());
    }

    @Disabled("B2: rellenar un hueco ya no paga hitos de racha. Lo cubre PremioPorPasoTest.hito_elTercerDiaHechoSeCobraUnaVez_yNiRellenarNiRehacerLoDuplican")
    @Test
    void alRellenarUnHuecoQueSaltaElHito_seCobraElHitoIntermedio() {
        // Mismo patrón de día fijo que alCompletarUnaFechaPasada_seGuardaLaFechaYActualizaSuPeriodo:
        // un miércoles siempre tiene margen hacia atrás hasta el lunes de su semana.
        LocalDate miercoles = LocalDate.of(2026, 9, 9);
        LocalDate martes = miercoles.minusDays(1);
        LocalDate lunes = miercoles.minusDays(2);
        LocalDate domingo = lunes.minusDays(1);
        LocalDate sabado = lunes.minusDays(2);
        when(rachaService.hoyDe(any(Habito.class))).thenReturn(miercoles);

        racha.setRachaActual(2); // por debajo del hito 3

        // Sábado a miércoles están completados salvo el martes: rellenar ese
        // hueco une los dos tramos y la racha pasa de 2 a 5 de un salto,
        // cruzando el hito de 3 sin caer justo en él.
        List<Registro> historico = List.of(
                new Registro(habito, true, "", sabado),
                new Registro(habito, true, "", domingo),
                new Registro(habito, true, "", lunes),
                new Registro(habito, true, "", martes),
                new Registro(habito, true, "", miercoles)
        );
        when(registroDAO.findByHabitoAndRango(eq(habito), eq(martes), eq(martes)))
                .thenReturn(new ArrayList<>());
        when(registroDAO.findByHabito(habito)).thenReturn(historico);
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "", martes);

        assertEquals(5, racha.getRachaActual());
        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(50), eq("HITO_RACHA"), eq(habito.getHabitoId()), anyString()
        );
    }

    @Test
    void alDeshacerUnRegistroAnteriorAlLunes_seRechaza() {
        LocalDate lunes = HOY.with(DayOfWeek.MONDAY);
        LocalDate fechaAnterior = lunes.minusDays(1);
        Registro registro = new Registro(habito, true, "", fechaAnterior);
        registro.setRegistroId(99);

        when(registroDAO.findById(99)).thenReturn(registro);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        // Nunca debería llegar a leerse: el suelo del lunes corta antes. Se
        // deja preparado en modo lenient para que, si algún día ese corte se
        // rompe, el test siga señalando el fallo real en vez de un
        // NullPointerException encubierto.
        lenient().when(reversionRegistroDAO.findByRegistro(99))
                .thenReturn(new ReversionRegistro(registro, null, null, null, null, null, null, 0));

        assertThrows(ConflictoException.class, () -> registroService.deshacerRegistro(99));
    }

    @Test
    void alAlcanzarLaMeta_laInstantaneaGuardaLaXpQueSeDio() {
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        ArgumentCaptor<Integer> xp = ArgumentCaptor.forClass(Integer.class);
        verify(mascotaService).ganarExperiencia(eq(1), xp.capture());
        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(xp.getValue(), captor.getValue().getMascotaExperienciaOtorgada());
    }

    @Disabled("B1: cada paso cobra ya su parte de XP. Lo sustituye unPasoParcial_laInstantaneaGuardaLaXpDeEsePaso")
    @Test
    void sinAlcanzarLaMeta_laInstantaneaGuardaCeroXp() {
        habito.setMeta(3);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(List.of(new Registro(habito, true, "", HOY)));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        registroService.completarHabito(habito, "");

        verify(mascotaService, never()).ganarExperiencia(anyInt(), anyInt());
        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(0, captor.getValue().getMascotaExperienciaOtorgada());
    }

    @Test
    void alDeshacer_seRestaLaXpQueDioEnVezDeFijarUnValor() {
        Registro registro = new Registro(habito, true, "", HOY);
        registro.setRegistroId(99);
        LocalDate diaPrevio = HOY.minusDays(1);
        ReversionRegistro reversion =
                new ReversionRegistro(registro, null, null, null, null, 5, diaPrevio, 0);

        when(registroDAO.findById(99)).thenReturn(registro);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(reversionRegistroDAO.findByRegistro(99)).thenReturn(reversion);

        registroService.deshacerRegistro(99);

        verify(mascotaService).restarExperiencia(1, 5);
        verify(mascotaService).restaurarDiaCompleto(1, diaPrevio);
    }

    @Test
    void alDeshacerUnaReversionSinXpRegistrada_noSeTocaLaMascota() {
        Registro registro = new Registro(habito, true, "", HOY);
        registro.setRegistroId(99);
        ReversionRegistro reversion =
                new ReversionRegistro(registro, null, null, null, null, null, null, 0);

        when(registroDAO.findById(99)).thenReturn(registro);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(reversionRegistroDAO.findByRegistro(99)).thenReturn(reversion);

        registroService.deshacerRegistro(99);

        verify(mascotaService, never()).restarExperiencia(anyInt(), anyInt());
        verify(mascotaService, never()).restaurarDiaCompleto(anyInt(), any());
    }

    @Test
    void alDeshacerAlgoDeHoySinRomperElDia_laFechaNoRetrocede() {
        Registro registro = new Registro(habito, true, "", HOY);
        registro.setRegistroId(99);
        ReversionRegistro reversion =
                new ReversionRegistro(registro, null, null, null, null, 5, HOY.minusDays(1), 0);

        when(registroDAO.findById(99)).thenReturn(registro);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(reversionRegistroDAO.findByRegistro(99)).thenReturn(reversion);
        when(habitoService.esDiaCompleto(usuario)).thenReturn(true);

        registroService.deshacerRegistro(99);

        verify(mascotaService).restarExperiencia(1, 5);
        verify(mascotaService, never()).restaurarDiaCompleto(anyInt(), any());
    }

    @Test
    void alDeshacerUnDiaPasado_laFechaNoSeToca() {
        // Mismo patrón de día fijo que alRellenarUnHuecoQueSaltaElHito_seCobraElHitoIntermedio:
        // un miércoles siempre tiene margen hacia atrás hasta el lunes de su semana.
        LocalDate miercoles = LocalDate.of(2026, 9, 9);
        LocalDate martes = miercoles.minusDays(1);
        when(rachaService.hoyDe(any(Habito.class))).thenReturn(miercoles);

        Registro registro = new Registro(habito, true, "", martes);
        registro.setRegistroId(99);
        ReversionRegistro reversion =
                new ReversionRegistro(registro, null, null, null, null, 5, martes.minusDays(1), 0);

        when(registroDAO.findById(99)).thenReturn(registro);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(reversionRegistroDAO.findByRegistro(99)).thenReturn(reversion);

        registroService.deshacerRegistro(99);

        verify(mascotaService).restarExperiencia(1, 5);
        verify(mascotaService, never()).restaurarDiaCompleto(anyInt(), any());
    }

    @Test
    void siLasLecturasDeRachaNoCompartenInstancia_laRachaMuertaNoContinua() {
        // Tres instancias distintas de la misma racha muerta: 6 días, última
        // fecha hace un mes. Simula lo que devolvería el DAO si cada lectura
        // abriera su propia transacción y la entidad saliera desconectada.
        Racha primera = new Racha(habito, HOY);
        primera.setRachaActual(6);
        primera.setUltimaFecha(HOY.minusMonths(1));
        Racha segunda = new Racha(habito, HOY);
        segunda.setRachaActual(6);
        segunda.setUltimaFecha(HOY.minusMonths(1));
        Racha tercera = new Racha(habito, HOY);
        tercera.setRachaActual(6);
        tercera.setUltimaFecha(HOY.minusMonths(1));

        when(rachaDAO.findByHabito(habito)).thenReturn(primera, segunda, tercera);

        // La normalización toca sólo la instancia que recibe, como en producción.
        when(rachaService.rachaActualVigente(any(Racha.class))).thenAnswer(inv -> {
            Racha r = inv.getArgument(0);
            r.setRachaActual(0);
            return 0;
        });

        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        // La racha se lee UNA sola vez: es lo que impide que una normalización
        // a 0 se pierda por leer otra instancia después.
        verify(rachaDAO, times(1)).findByHabito(habito);

        // Esa única instancia es la que rachaActualVigente puso a 0 y la que
        // actualizarRacha sube a 1: una racha muerta que se completa hoy
        // empieza de nuevo, no continúa.
        assertEquals(1, primera.getRachaActual());
    }

    @Test
    void unPasoParcialDeUnDiario_cobraSuParteEnElMomento() {
        // Diario de 4 con dos veces hechas: la tercera cobra 6 puntos y 1 de XP.
        habito.setMeta(4);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(List.of(
                        new Registro(habito, true, "", HOY),
                        new Registro(habito, true, "", HOY)));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(6), eq("HABITO_COMPLETADO"), eq(habito.getHabitoId()), anyString());
        verify(mascotaService).ganarExperiencia(1, 1);
        assertEquals(6, resultado.get("puntosGanados"));
        assertEquals(6, resultado.get("puntosPaso"));
        assertEquals(0, resultado.get("puntosExtraSemana"));
        assertEquals(0, resultado.get("puntosHito"));
        assertEquals(1, resultado.get("xpGanada"));
    }

    @Test
    void unPasoQueNoDaExperiencia_cobraPuntosYNoLlamaALaMascota() {
        // Diario de 6, primera vez: 4 puntos y 0 de XP.
        habito.setMeta(6);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(4), eq("HABITO_COMPLETADO"), eq(habito.getHabitoId()), anyString());
        verify(mascotaService, never()).ganarExperiencia(anyInt(), anyInt());
        assertEquals(4, resultado.get("puntosGanados"));
        assertEquals(0, resultado.get("xpGanada"));
        assertEquals(false, resultado.get("subioNivel"));
        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(0, captor.getValue().getMascotaExperienciaOtorgada());
    }

    @Test
    void laSesionQueCompletaLaSemana_cobraSuDiaYElExtraAparte() {
        // Gimnasio de 5 con cuatro sesiones hechas: la quinta cobra 25 puntos y
        // 5 de XP por la sesión, y 25 puntos y 5 de XP más por completar la semana.
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(5);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(List.of(
                        new Registro(habito, true, "", HOY),
                        new Registro(habito, true, "", HOY),
                        new Registro(habito, true, "", HOY),
                        new Registro(habito, true, "", HOY)));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(25), eq("HABITO_COMPLETADO"), eq(habito.getHabitoId()), anyString());
        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(25), eq("EXTRA_SEMANA_COMPLETA"), eq(habito.getHabitoId()), anyString());
        verify(mascotaService).ganarExperiencia(1, 10);
        assertEquals(50, resultado.get("puntosGanados"));
        assertEquals(25, resultado.get("puntosPaso"));
        assertEquals(25, resultado.get("puntosExtraSemana"));
        assertEquals(0, resultado.get("puntosHito"));
        assertEquals(10, resultado.get("xpGanada"));
        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(10, captor.getValue().getMascotaExperienciaOtorgada());
    }

    @Test
    void pasarseDelObjetivoSemanal_noDaNada() {
        // Semanal de 2 con la semana ya completa: una tercera sesión se apunta,
        // pero no cobra puntos ni XP.
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(2);
        racha.setRachaActual(1);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().rangoPeriodo(HOY)[0]);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(List.of(
                        new Registro(habito, true, "", HOY),
                        new Registro(habito, true, "", HOY)));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(registroDAO).save(any(Registro.class));
        verify(usuarioMonedaService, never()).registrarMovimiento(
                any(), anyInt(), anyString(), any(), any());
        verify(mascotaService, never()).ganarExperiencia(anyInt(), anyInt());
        assertEquals(0, resultado.get("puntosGanados"));
        assertEquals(0, resultado.get("xpGanada"));
    }

    @Test
    void unPasoParcial_laInstantaneaGuardaLaXpDeEsePaso() {
        // Diario de 3 con una vez hecha: la XP se reparte 1, 2 y 2, así que la
        // segunda vez da 2.
        habito.setMeta(3);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(List.of(new Registro(habito, true, "", HOY)));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        verify(mascotaService).ganarExperiencia(1, 2);
        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(2, captor.getValue().getMascotaExperienciaOtorgada());
    }

    @Test
    void alLlegarATresDiasHechos_seCobraElHitoAunqueNoSeanSeguidos() {
        // Dos días hechos con huecos por medio; el de hoy es el tercero.
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(
                new Registro(habito, true, "", HOY.minusDays(10)),
                new Registro(habito, true, "", HOY.minusDays(5))));
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(50), eq("HITO_DIAS_HECHOS"), eq(habito.getHabitoId()), anyString());
        assertEquals(50, resultado.get("puntosHito"));
        assertEquals(75, resultado.get("puntosGanados"));
    }

    @Test
    void unHitoYaSuperado_noSeCobraAunqueLaRachaLlegueATres() {
        // Ocho días hechos: los hitos de 3 y de 7 se dan por pagados. La racha
        // viva de 2 sube hoy a 3, que con el sistema viejo pagaba un hito.
        racha.setRachaActual(2);
        racha.setRachaMaxima(2);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().inicioPeriodoAnterior(ZONA));
        List<Registro> ochoDias = new ArrayList<>();
        for (int dia = 1; dia <= 8; dia++) {
            ochoDias.add(new Registro(habito, true, "", HOY.minusDays(dia)));
        }
        when(registroDAO.findByHabito(habito)).thenReturn(ochoDias);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService, never()).registrarMovimiento(
                any(), anyInt(), eq("HITO_DIAS_HECHOS"), any(), any());
        verify(usuarioMonedaService, never()).registrarMovimiento(
                any(), anyInt(), eq("HITO_RACHA"), any(), any());
        assertEquals(0, resultado.get("puntosHito"));
        assertEquals(25, resultado.get("puntosGanados"));
    }

    @Test
    void enUnDiario_elDiaCuentaComoHechoAlLlegarAlObjetivo() {
        // Diario de 2: dos días completos antes y hoy va 1 de 2. El segundo
        // de hoy completa el tercer día hecho.
        habito.setMeta(2);
        Registro elDeHoy = new Registro(habito, true, "", HOY);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(
                new Registro(habito, true, "", HOY.minusDays(3)),
                new Registro(habito, true, "", HOY.minusDays(3)),
                new Registro(habito, true, "", HOY.minusDays(2)),
                new Registro(habito, true, "", HOY.minusDays(2)),
                elDeHoy));
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(List.of(elDeHoy));
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(50), eq("HITO_DIAS_HECHOS"), eq(habito.getHabitoId()), anyString());
        assertEquals(13, resultado.get("puntosPaso"));
        assertEquals(50, resultado.get("puntosHito"));
        assertEquals(63, resultado.get("puntosGanados"));
    }

    @Test
    void enUnSemanal_cadaSesionCuentaComoDiaHecho() {
        // Semanal de 5 con una sesión en cada una de las dos semanas
        // anteriores: la de hoy es su tercer día hecho.
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(5);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(
                new Registro(habito, true, "", HOY.minusDays(14)),
                new Registro(habito, true, "", HOY.minusDays(7))));
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(50), eq("HITO_DIAS_HECHOS"), eq(habito.getHabitoId()), anyString());
        assertEquals(25, resultado.get("puntosPaso"));
        assertEquals(0, resultado.get("puntosExtraSemana"));
        assertEquals(50, resultado.get("puntosHito"));
        assertEquals(75, resultado.get("puntosGanados"));
    }

    @Test
    void alVolverTrasTresDiasSinHacerlo_seConcedeResilienciaYEntraEnLaInstantanea() {
        // Hecho por última vez hace cuatro días: tres días enteros sin hacerlo.
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(
                new Registro(habito, true, "", HOY.minusDays(4))));
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(logrosHabitosService.evaluarTrasVolver(usuario))
                .thenReturn(List.of("RACHA_RECUPERADA"));
        Logro resiliencia = new Logro();
        resiliencia.setLogroId(77);
        when(logroDAO.findByCodigo("RACHA_RECUPERADA")).thenReturn(resiliencia);
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> resultado = registroService.completarHabito(habito, "");

        assertEquals(List.of("RACHA_RECUPERADA"), resultado.get("logros"));
        ArgumentCaptor<ReversionRegistro> captor =
                ArgumentCaptor.forClass(ReversionRegistro.class);
        verify(reversionRegistroDAO).save(captor.capture());
        assertEquals(1, captor.getValue().getLogros().size());
        assertEquals(77, captor.getValue().getLogros().get(0).getLogroRef());
    }

    @Test
    void sinTresDiasSinHacerlo_noSeEvaluaLaVuelta() {
        // Hecho anteayer: solo un día sin hacerlo.
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(
                new Registro(habito, true, "", HOY.minusDays(2))));
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any()))
                .thenReturn(new ArrayList<>());
        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito))
                .thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt()))
                .thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        registroService.completarHabito(habito, "");

        verify(logrosHabitosService, never()).evaluarTrasVolver(any());
    }

}
