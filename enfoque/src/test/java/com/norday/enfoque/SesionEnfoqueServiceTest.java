package com.norday.enfoque;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.TextosService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.enfoque.model.EstadoAporte;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.MotivosEnfoque;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.model.dto.PeticionSesionDTO;
import com.norday.enfoque.model.dto.PeticionSesionDTO.DestinoDTO;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.enfoque.service.RelojEnfoque;
import com.norday.enfoque.service.SesionEnfoqueService;
import com.norday.enfoque.service.SesionEnfoqueService.Resultado;
import com.norday.enfoque.service.SesionInvalidaException;
import com.norday.gamificacion.model.ResultadoAporte;
import com.norday.gamificacion.service.DestinoMinutos;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Registrar una sesión: lo que se guarda (con ArgumentCaptor sobre el DAO),
 * cuándo se llama al destino y con qué, y las reglas de la petición.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SesionEnfoqueServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    /** Viernes 9-oct-2026, 10:00 en Madrid. */
    private static final Instant AHORA = Instant.parse("2026-10-09T08:00:00Z");
    private static final UUID CLAVE = UUID.fromString("3f1c6a52-6a0e-4c43-9a59-0b4f3c7c2b11");

    @Mock private ISesionEnfoqueDAO sesionDAO;
    @Mock private DestinoMinutos destinoHabito;
    @Mock private ZonaUsuarioService zonaUsuarioService;
    @Mock private TextosService textosService;
    @Mock private RelojEnfoque reloj;
    @Mock private PlatformTransactionManager transactionManager;

    private SesionEnfoqueService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        servicio = new SesionEnfoqueService();
        ReflectionTestUtils.setField(servicio, "sesionDAO", sesionDAO);
        ReflectionTestUtils.setField(servicio, "destinos", List.of(destinoHabito));
        ReflectionTestUtils.setField(servicio, "zonaUsuarioService", zonaUsuarioService);
        ReflectionTestUtils.setField(servicio, "textosService", textosService);
        ReflectionTestUtils.setField(servicio, "reloj", reloj);
        ReflectionTestUtils.setField(servicio, "transactionManager", transactionManager);
        usuario = new Usuario();
        usuario.setUsuarioId(7);
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(MADRID);
        when(reloj.ahora()).thenReturn(AHORA);
        when(textosService.texto(anyString(), eq(usuario), any(Object[].class)))
                .thenAnswer(i -> i.getArgument(0));
        when(destinoHabito.tipo()).thenReturn("habito");
        when(sesionDAO.insertarSiClaveLibre(any())).thenReturn(41);
        // findById y releer devuelven lo último que se insertó, con el aporte
        // que se apuntó: el DAO de mentira se comporta como la tabla.
        when(sesionDAO.findById(41)).thenAnswer(i -> insertada());
        when(sesionDAO.releer(41)).thenAnswer(i -> {
            SesionEnfoque s = insertada();
            ArgumentCaptor<EstadoAporte> estado = ArgumentCaptor.forClass(EstadoAporte.class);
            ArgumentCaptor<String> motivo = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<Integer> minutos = ArgumentCaptor.forClass(Integer.class);
            verify(sesionDAO, atLeastOnce()).guardarAporte(eq(41), estado.capture(), motivo.capture(),
                    minutos.capture());
            s.setAporteEstado(estado.getValue());
            s.setAporteMotivo(motivo.getValue());
            s.setMinutosAportados(minutos.getValue());
            return s;
        });
    }

    private SesionEnfoque insertada() {
        ArgumentCaptor<SesionEnfoque> captor = ArgumentCaptor.forClass(SesionEnfoque.class);
        verify(sesionDAO, atLeastOnce()).insertarSiClaveLibre(captor.capture());
        SesionEnfoque s = captor.getValue();
        s.setId(41);
        return s;
    }

    private SesionEnfoque guardadaEnElAlta() {
        ArgumentCaptor<SesionEnfoque> captor = ArgumentCaptor.forClass(SesionEnfoque.class);
        verify(sesionDAO).insertarSiClaveLibre(captor.capture());
        return captor.getValue();
    }

    private static PeticionSesionDTO peticion(String inicio, int duracionSeg, int objetivo, String estado,
                                              DestinoDTO destino) {
        return new PeticionSesionDTO(CLAVE.toString(), inicio, duracionSeg, objetivo, estado, destino);
    }

    /** 25 min completados que terminan a las 9:50 de Madrid. */
    private static PeticionSesionDTO peticion25(DestinoDTO destino) {
        return peticion("2026-10-09T07:25:00Z", 1500, 25, "COMPLETADA", destino);
    }

    private static ResultadoAporte aceptado() {
        return new ResultadoAporte(ResultadoAporte.Estado.ACEPTADO, null, 12, 2, 25, 30, false, List.of("X"));
    }

    // ── Paso 1: lo que se guarda ─────────────────────────────────

    @Test
    void sinDestino_guardaLaSesionSinDestino_yNoLlamaANadie() {
        Resultado resultado = servicio.registrar(usuario, peticion25(null));

        SesionEnfoque guardada = guardadaEnElAlta();
        assertEquals(usuario, guardada.getUsuario());
        assertEquals(CLAVE, guardada.getClave());
        assertEquals(LocalDateTime.of(2026, 10, 9, 7, 25), guardada.getInicioUtc());
        assertEquals(1500, guardada.getDuracionSeg());
        assertEquals(25, guardada.getDuracionObjetivoMin());
        assertEquals(EstadoSesion.COMPLETADA, guardada.getEstado());
        assertEquals(LocalDate.of(2026, 10, 9), guardada.getDia());
        assertNull(guardada.getDestinoTipo());
        assertNull(guardada.getDestinoRef());
        assertEquals(EstadoAporte.SIN_DESTINO, guardada.getAporteEstado());
        assertEquals(0, guardada.getMinutosAportados());
        assertEquals(LocalDateTime.ofInstant(AHORA, ZoneOffset.UTC), guardada.getCreadoEn());
        verify(destinoHabito, never()).aportar(any(), any(), anyInt(), any(), any(), any());
        verify(sesionDAO, never()).guardarAporte(anyInt(), any(), any(), anyInt());
        assertTrue(resultado.nueva());
        assertEquals(EstadoAporte.SIN_DESTINO, resultado.aporte().estado());
    }

    @Test
    void elDia_esElDeInicioEnLaZonaDelUsuario() {
        // 23:30 UTC del jueves son las 01:30 del viernes en Madrid.
        servicio.registrar(usuario, peticion("2026-10-08T23:30:00Z", 1500, 25, "COMPLETADA", null));
        assertEquals(LocalDate.of(2026, 10, 9), guardadaEnElAlta().getDia());
    }

    @Test
    void conDestino_guardaPendiente_aportaLosMinutosEnteros_yApuntaElResultado() {
        when(destinoHabito.aportar(any(), any(), anyInt(), any(), any(), any())).thenReturn(aceptado());

        // 1559 s son 25 minutos enteros, no 26.
        Resultado resultado = servicio.registrar(usuario,
                peticion("2026-10-09T07:20:00Z", 1559, 25, "COMPLETADA", new DestinoDTO("habito", 3L)));

        SesionEnfoque alta = guardadaEnElAlta();
        assertEquals("habito", alta.getDestinoTipo());
        assertEquals(3L, alta.getDestinoRef());
        verify(destinoHabito).aportar(7L, 3L, 25, CLAVE, LocalDate.of(2026, 10, 9), "ENFOQUE");
        verify(sesionDAO).guardarAporte(41, EstadoAporte.ACEPTADO, null, 25);
        assertEquals(EstadoAporte.ACEPTADO, resultado.aporte().estado());
        assertEquals(25, resultado.aporte().minutos());
        assertEquals(12, resultado.aporte().monedas());
        assertEquals(2, resultado.aporte().xp());
        assertEquals(25, resultado.aporte().minutosDia());
        assertEquals(30, resultado.aporte().minutosObjetivo());
        assertEquals(List.of("X"), resultado.aporte().logros());
    }

    @Test
    void elAlta_quedaPendienteAntesDeAportar() {
        when(destinoHabito.aportar(any(), any(), anyInt(), any(), any(), any())).thenAnswer(i -> {
            assertEquals(EstadoAporte.PENDIENTE, guardadaEnElAlta().getAporteEstado(),
                    "la sesión ya está guardada, PENDIENTE, cuando se aporta");
            return aceptado();
        });
        servicio.registrar(usuario, peticion25(new DestinoDTO("habito", 3L)));
        verify(destinoHabito).aportar(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void unRechazoDelDestino_seApuntaConSuMotivo_ySinMinutos() {
        when(destinoHabito.aportar(any(), any(), anyInt(), any(), any(), any()))
                .thenReturn(ResultadoAporte.rechazado("aporte.habito.pausado"));
        Resultado resultado = servicio.registrar(usuario, peticion25(new DestinoDTO("habito", 3L)));
        verify(sesionDAO).guardarAporte(41, EstadoAporte.RECHAZADO, "aporte.habito.pausado", 0);
        assertEquals(EstadoAporte.RECHAZADO, resultado.aporte().estado());
        assertEquals("aporte.habito.pausado", resultado.aporte().motivo());
        assertEquals(0, resultado.aporte().minutos());
    }

    @Test
    void perdida_conDestino_noAporta_ySeGuardaRechazada() {
        Resultado resultado = servicio.registrar(usuario,
                peticion("2026-10-09T07:00:00Z", 900, 25, "PERDIDA", new DestinoDTO("habito", 3L)));
        SesionEnfoque alta = guardadaEnElAlta();
        assertEquals(EstadoAporte.RECHAZADO, alta.getAporteEstado());
        assertEquals(MotivosEnfoque.SESION_PERDIDA, alta.getAporteMotivo());
        assertEquals(0, alta.minutos());
        verify(destinoHabito, never()).aportar(any(), any(), anyInt(), any(), any(), any());
        assertEquals(EstadoAporte.RECHAZADO, resultado.aporte().estado());
    }

    @Test
    void unTipoQueNadieDeclara_seRechaza_sinLlamarAlDestino() {
        servicio.registrar(usuario, peticion25(new DestinoDTO("libro", 3L)));
        verify(destinoHabito, never()).aportar(any(), any(), anyInt(), any(), any(), any());
        verify(sesionDAO).guardarAporte(41, EstadoAporte.RECHAZADO, MotivosEnfoque.DESTINO_DESCONOCIDO, 0);
    }

    @Test
    void unFalloTecnicoDelDestino_sale_yLaSesionQuedaPendiente() {
        when(destinoHabito.aportar(any(), any(), anyInt(), any(), any(), any()))
                .thenThrow(new IllegalStateException("la base de datos se ha ido"));
        assertThrows(IllegalStateException.class,
                () -> servicio.registrar(usuario, peticion25(new DestinoDTO("habito", 3L))));
        assertEquals(EstadoAporte.PENDIENTE, guardadaEnElAlta().getAporteEstado());
        verify(sesionDAO, never()).guardarAporte(anyInt(), any(), any(), anyInt());
    }

    // ── Reintentos ───────────────────────────────────────────────

    private SesionEnfoque yaGuardada(EstadoAporte aporte) {
        SesionEnfoque s = new SesionEnfoque();
        s.setId(41);
        s.setUsuario(usuario);
        s.setClave(CLAVE);
        s.setInicioUtc(LocalDateTime.of(2026, 10, 9, 7, 25));
        s.setDuracionSeg(1500);
        s.setDuracionObjetivoMin(25);
        s.setEstado(EstadoSesion.COMPLETADA);
        s.setDia(LocalDate.of(2026, 10, 9));
        s.setDestinoTipo("habito");
        s.setDestinoRef(3L);
        s.setAporteEstado(aporte);
        s.setMinutosAportados(aporte == EstadoAporte.ACEPTADO ? 25 : 0);
        s.setCreadoEn(LocalDateTime.of(2026, 10, 9, 7, 51));
        return s;
    }

    @Test
    void reintento_deUnaSesionCerrada_devuelveLaGuardada_sinTocarNada() {
        SesionEnfoque guardada = yaGuardada(EstadoAporte.ACEPTADO);
        when(sesionDAO.findByClave(CLAVE)).thenReturn(guardada);

        Resultado resultado = servicio.registrar(usuario, peticion25(new DestinoDTO("habito", 3L)));

        assertFalse(resultado.nueva());
        assertSame(guardada, resultado.sesion());
        assertEquals(EstadoAporte.ACEPTADO, resultado.aporte().estado());
        assertEquals(25, resultado.aporte().minutos());
        assertEquals(0, resultado.aporte().monedas());
        verify(sesionDAO, never()).insertarSiClaveLibre(any());
        verify(destinoHabito, never()).aportar(any(), any(), anyInt(), any(), any(), any());
        verify(sesionDAO, never()).guardarAporte(anyInt(), any(), any(), anyInt());
    }

    @Test
    void reintento_deUnaSesionPendiente_laCompleta() {
        SesionEnfoque guardada = yaGuardada(EstadoAporte.PENDIENTE);
        when(sesionDAO.findByClave(CLAVE)).thenReturn(guardada);
        SesionEnfoque completada = yaGuardada(EstadoAporte.REPETIDO);
        completada.setMinutosAportados(25);
        doReturn(completada).when(sesionDAO).releer(41);
        when(destinoHabito.aportar(any(), any(), anyInt(), any(), any(), any())).thenReturn(
                new ResultadoAporte(ResultadoAporte.Estado.REPETIDO, null, 0, 0, 25, 30, false, List.of()));

        Resultado resultado = servicio.registrar(usuario, peticion25(new DestinoDTO("habito", 3L)));

        assertFalse(resultado.nueva());
        verify(sesionDAO, never()).insertarSiClaveLibre(any());
        verify(destinoHabito).aportar(7L, 3L, 25, CLAVE, LocalDate.of(2026, 10, 9), "ENFOQUE");
        verify(sesionDAO).guardarAporte(41, EstadoAporte.REPETIDO, null, 25);
        assertEquals(EstadoAporte.REPETIDO, resultado.aporte().estado());
    }

    @Test
    void reintento_conOtroCuerpo_es409() {
        when(sesionDAO.findByClave(CLAVE)).thenReturn(yaGuardada(EstadoAporte.ACEPTADO));
        assertThrows(ConflictoException.class, () -> servicio.registrar(usuario,
                peticion("2026-10-09T07:25:00Z", 1560, 25, "COMPLETADA", new DestinoDTO("habito", 3L))));
        assertThrows(ConflictoException.class, () -> servicio.registrar(usuario, peticion25(null)));
        assertThrows(ConflictoException.class, () -> servicio.registrar(usuario,
                peticion25(new DestinoDTO("habito", 4L))));
        verify(sesionDAO, never()).insertarSiClaveLibre(any());
        verify(destinoHabito, never()).aportar(any(), any(), anyInt(), any(), any(), any());
    }

    @Test
    void laClaveDeOtraCuenta_es409() {
        SesionEnfoque ajena = yaGuardada(EstadoAporte.ACEPTADO);
        Usuario otro = new Usuario();
        otro.setUsuarioId(8);
        ajena.setUsuario(otro);
        when(sesionDAO.findByClave(CLAVE)).thenReturn(ajena);
        assertThrows(ConflictoException.class,
                () -> servicio.registrar(usuario, peticion25(new DestinoDTO("habito", 3L))));
    }

    @Test
    void elAltaBloqueaLasAltasDelUsuario_antesDeBuscarLaClave() {
        servicio.registrar(usuario, peticion25(null));
        var orden = inOrder(sesionDAO);
        orden.verify(sesionDAO).bloquearAltasDe(7);
        orden.verify(sesionDAO).findByClave(CLAVE);
        orden.verify(sesionDAO).haySolape(eq(7), any(), any());
        orden.verify(sesionDAO).insertarSiClaveLibre(any());
    }

    @Test
    void solape_es409_yNoGuarda() {
        when(sesionDAO.haySolape(7, LocalDateTime.of(2026, 10, 9, 7, 25), LocalDateTime.of(2026, 10, 9, 7, 50)))
                .thenReturn(true);
        assertThrows(ConflictoException.class, () -> servicio.registrar(usuario, peticion25(null)));
        verify(sesionDAO, never()).insertarSiClaveLibre(any());
    }

    // ── Validaciones ─────────────────────────────────────────────

    private void invalida(PeticionSesionDTO peticion, String clave) {
        SesionInvalidaException e = assertThrows(SesionInvalidaException.class,
                () -> servicio.registrar(usuario, peticion));
        assertEquals(clave, e.getMessage());
        verify(sesionDAO, never()).insertarSiClaveLibre(any());
    }

    @Test
    void validaciones() {
        invalida(null, "enfoque.peticion.clave");
        invalida(new PeticionSesionDTO("1-1-1-1-1", "2026-10-09T07:25:00Z", 1500, 25, "COMPLETADA", null),
                "enfoque.peticion.clave");
        invalida(peticion("ayer", 1500, 25, "COMPLETADA", null), "enfoque.peticion.inicio");
        invalida(peticion("2026-10-09T07:25:00Z", 1500, 25, "ABANDONADA", null), "enfoque.peticion.estado");
        invalida(peticion("2026-10-09T07:25:00Z", 1500, 25, null, null), "enfoque.peticion.estado");
        for (int objetivo : new int[]{5, 9, 12, 121, 125}) {
            invalida(peticion("2026-10-09T07:25:00Z", 1500, objetivo, "COMPLETADA", null),
                    "enfoque.peticion.objetivo");
        }
        invalida(peticion("2026-10-09T07:25:00Z", -1, 25, "PERDIDA", null), "enfoque.peticion.duracion");
        invalida(peticion("2026-10-09T05:00:00Z", 7261, 120, "COMPLETADA", null),
                "enfoque.peticion.duracion.maxima");
        invalida(peticion("2026-10-09T07:25:00Z", 599, 25, "TERMINADA", null), "enfoque.peticion.duracion.minima");
        invalida(peticion("2026-10-09T07:25:00Z", 599, 25, "COMPLETADA", null), "enfoque.peticion.duracion.minima");
        invalida(peticion("2026-10-09T07:25:00Z", 1500, 25, "COMPLETADA", new DestinoDTO(" ", 3L)),
                "enfoque.peticion.destino");
        invalida(peticion("2026-10-09T07:25:00Z", 1500, 25, "COMPLETADA", new DestinoDTO("habito", null)),
                "enfoque.peticion.destino");
        invalida(peticion("2026-10-09T07:25:00Z", 1500, 25, "COMPLETADA", new DestinoDTO("habito", 0L)),
                "enfoque.peticion.destino");
        invalida(peticion("2026-10-09T07:25:00Z", 1500, 25, "COMPLETADA",
                new DestinoDTO("x".repeat(21), 3L)), "enfoque.peticion.destino");
        // Termina 2 min y 1 s después de ahora.
        invalida(peticion("2026-10-09T07:37:01Z", 1500, 25, "COMPLETADA", null), "enfoque.peticion.futura");
        // Empezó hace 7 días y 1 s.
        invalida(peticion("2026-10-02T07:59:59Z", 1500, 25, "COMPLETADA", null), "enfoque.peticion.antigua");
    }

    @Test
    void losBordesQueSiValen() {
        servicio.registrar(usuario, peticion("2026-10-09T07:37:00Z", 1500, 25, "COMPLETADA", null));
        servicio.registrar(usuario, peticion("2026-10-02T08:00:00Z", 600, 10, "TERMINADA", null));
        servicio.registrar(usuario, peticion("2026-10-09T05:00:00Z", 7260, 120, "COMPLETADA", null));
        servicio.registrar(usuario, peticion("2026-10-09T07:00:00Z", 0, 25, "PERDIDA", null));
        verify(sesionDAO, times(4)).insertarSiClaveLibre(any());
    }

    @Test
    void losMinutos_sonLosEnterosTranscurridos_ySoloSiCuenta() {
        SesionEnfoque s = new SesionEnfoque();
        s.setDuracionSeg(1499);
        s.setEstado(EstadoSesion.TERMINADA);
        assertEquals(24, s.minutos());
        s.setEstado(EstadoSesion.COMPLETADA);
        s.setDuracionSeg(1500);
        assertEquals(25, s.minutos());
        s.setEstado(EstadoSesion.PERDIDA);
        assertEquals(0, s.minutos());
    }

    // ── Resumen e historial ──────────────────────────────────────

    private SesionEnfoque sesionDel(LocalDate dia, int segundos, EstadoSesion estado) {
        SesionEnfoque s = new SesionEnfoque();
        s.setDia(dia);
        s.setDuracionSeg(segundos);
        s.setEstado(estado);
        return s;
    }

    @Test
    void resumen_cuentaSoloLasQueCuentan_conTodosLosDias() {
        LocalDate lunes = LocalDate.of(2026, 10, 5);
        when(sesionDAO.findByUsuarioEntre(7, lunes, lunes.plusDays(6))).thenReturn(List.of(
                sesionDel(lunes, 1500, EstadoSesion.COMPLETADA),
                sesionDel(lunes, 900, EstadoSesion.PERDIDA),
                sesionDel(lunes.plusDays(2), 610, EstadoSesion.TERMINADA)));
        var resumen = servicio.resumen(usuario, "2026-10-05", "2026-10-11");
        assertEquals(7, resumen.dias().size());
        assertEquals(25, resumen.dias().get(0).minutos());
        assertEquals(1, resumen.dias().get(0).sesiones());
        assertEquals(0, resumen.dias().get(1).minutos());
        assertEquals(10, resumen.dias().get(2).minutos());
        assertEquals(35, resumen.totalMinutos());
        assertEquals(2, resumen.totalSesiones());
    }

    @Test
    void resumen_rangoYFechas() {
        assertThrows(SesionInvalidaException.class, () -> servicio.resumen(usuario, "2026-10-11", "2026-10-05"));
        assertThrows(SesionInvalidaException.class, () -> servicio.resumen(usuario, "2026-08-08", "2026-10-09"));
        assertThrows(SesionInvalidaException.class, () -> servicio.resumen(usuario, null, "2026-10-09"));
        assertThrows(SesionInvalidaException.class, () -> servicio.resumen(usuario, "9-10-2026", "2026-10-09"));
        // 62 días justos valen.
        assertEquals(62, servicio.resumen(usuario, "2026-08-09", "2026-10-09").dias().size());
    }

    @Test
    void racha_diasSeguidosHastaHoyOAyer() {
        LocalDate hoy = LocalDate.of(2026, 10, 9);
        when(sesionDAO.diasQueCuentanHasta(7, hoy)).thenReturn(List.of(hoy, hoy.minusDays(1), hoy.minusDays(3)));
        assertEquals(2, servicio.resumen(usuario, "2026-10-09", "2026-10-09").rachaActual());

        when(sesionDAO.diasQueCuentanHasta(7, hoy)).thenReturn(List.of(hoy.minusDays(1), hoy.minusDays(2)));
        assertEquals(2, servicio.resumen(usuario, "2026-10-09", "2026-10-09").rachaActual(),
                "sin sesión hoy, la racha de ayer sigue viva");

        when(sesionDAO.diasQueCuentanHasta(7, hoy)).thenReturn(List.of(hoy.minusDays(2)));
        assertEquals(0, servicio.resumen(usuario, "2026-10-09", "2026-10-09").rachaActual());

        when(sesionDAO.diasQueCuentanHasta(7, hoy)).thenReturn(List.of());
        assertEquals(0, servicio.resumen(usuario, "2026-10-09", "2026-10-09").rachaActual());
    }

    @Test
    void historial_paginaYTamano() {
        when(sesionDAO.contar(7)).thenReturn(45L);
        var pagina = servicio.historial(usuario, 1, 20);
        verify(sesionDAO).pagina(7, 1, 20);
        assertTrue(pagina.hayMas());
        assertFalse(servicio.historial(usuario, 2, 20).hayMas());
        assertThrows(SesionInvalidaException.class, () -> servicio.historial(usuario, -1, 20));
        assertThrows(SesionInvalidaException.class, () -> servicio.historial(usuario, 0, 0));
        assertThrows(SesionInvalidaException.class, () -> servicio.historial(usuario, 0, 101));
    }
}
