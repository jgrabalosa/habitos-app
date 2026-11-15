package com.norday.habitos;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.gamificacion.model.DestinoDisponible;
import com.norday.gamificacion.model.ResultadoAporte;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.DestinoMinutosHabitos;
import com.norday.habitos.service.ObjetivoService;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import com.norday.habitos.service.RegistroService.Solicitud;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.TransactionStatus;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El adaptador de los hábitos al puerto DestinoMinutos: valida antes de
 * completar y responde RECHAZADO con su motivo sin llamar a completar; si
 * pasa, completa con los minutos, la clave y el origen ENFOQUE; y nunca deja
 * escapar una excepción de negocio, que se deshace en su propia transacción.
 *
 * "Hoy" es el viernes 9-oct-2026.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DestinoMinutosHabitosTest {

    private static final LocalDate HOY = LocalDate.of(2026, 10, 9);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final UUID CLAVE = UUID.fromString("3f1c6a52-6a0e-4c43-9a59-0b4f3c7c2b11");

    @Mock private IHabitoDAO habitoDAO;
    @Mock private IRegistroDAO registroDAO;
    @Mock private IUsuarioDAO usuarioDAO;
    @Mock private RegistroService registroService;
    @Mock private ObjetivoService objetivoService;
    @Mock private RachaService rachaService;
    @Mock private PlatformTransactionManager transactionManager;
    @Mock private TransactionStatus estadoTx;

    @InjectMocks
    private DestinoMinutosHabitos destino;

    private Usuario usuario;
    private Habito leer;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(7);
        leer = habito(10, "Leer", new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null));
        when(habitoDAO.findById(10)).thenReturn(leer);
        when(rachaService.hoyDe(any(Habito.class))).thenReturn(HOY);
        when(objetivoService.historiales(anyList())).thenAnswer(i -> {
            Map<Integer, HistorialObjetivos> mapa = new HashMap<>();
            for (Object h : (List<?>) i.getArgument(0)) {
                Habito habito = (Habito) h;
                mapa.put(habito.getHabitoId(), HistorialObjetivos.de(habito,
                        List.of(new HabitoObjetivo(habito, LocalDate.of(2026, 1, 1), Objetivo.de(habito)))));
            }
            return mapa;
        });
        when(transactionManager.getTransaction(any())).thenReturn(estadoTx);
    }

    private Habito habito(int id, String nombre, Objetivo objetivo) {
        Habito h = new Habito();
        h.setHabitoId(id);
        h.setNombre(nombre);
        h.setActivo(true);
        h.setPropietario(usuario);
        h.setFrecuencia(objetivo.frecuencia());
        h.setMeta(objetivo.meta());
        h.setUnidad(objetivo.unidad());
        h.setMinutosObjetivo(objetivo.minutosObjetivo());
        return h;
    }

    private static Map<String, Object> respuestaDeCompletar(boolean repetido) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("logros", List.of("RACHA_3"));
        r.put("puntosGanados", repetido ? 0 : 62);
        r.put("xpGanada", repetido ? 0 : 4);
        r.put("minutosDia", 25);
        r.put("minutosObjetivo", 30);
        r.put("diaHecho", false);
        r.put("repetido", repetido);
        return r;
    }

    private ResultadoAporte aportar(long habitoId, int minutos, LocalDate dia) {
        return destino.aportar(7L, habitoId, minutos, CLAVE, dia, "ENFOQUE");
    }

    private void rechazado(ResultadoAporte resultado, String motivo) {
        assertEquals(ResultadoAporte.Estado.RECHAZADO, resultado.estado());
        assertEquals(motivo, resultado.motivo());
        assertEquals(0, resultado.monedas());
        verify(registroService, never()).completar(any(), any());
    }

    // ── Aceptado ─────────────────────────────────────────────────

    @Test
    void completa_conLosMinutosLaClaveYElOrigen_igualQueElPanel() {
        when(registroService.completar(any(), any())).thenReturn(respuestaDeCompletar(false));

        ResultadoAporte resultado = aportar(10, 25, HOY);

        ArgumentCaptor<Solicitud> solicitud = ArgumentCaptor.forClass(Solicitud.class);
        verify(registroService).completar(same(leer), solicitud.capture());
        assertEquals("25", solicitud.getValue().minutos());
        assertEquals(CLAVE, solicitud.getValue().clave());
        assertEquals(OrigenRegistro.ENFOQUE, solicitud.getValue().origen());
        assertEquals(HOY, solicitud.getValue().fecha());
        assertNull(solicitud.getValue().nota());

        assertEquals(ResultadoAporte.Estado.ACEPTADO, resultado.estado());
        assertNull(resultado.motivo());
        assertEquals(62, resultado.monedas());
        assertEquals(4, resultado.xp());
        assertEquals(25, resultado.minutosDia());
        assertEquals(30, resultado.minutosObjetivo());
        assertEquals(false, resultado.diaHecho());
        assertEquals(List.of("RACHA_3"), resultado.logros());
    }

    @Test
    void unDiaPasadoDeEstaSemana_vale() {
        when(registroService.completar(any(), any())).thenReturn(respuestaDeCompletar(false));
        assertEquals(ResultadoAporte.Estado.ACEPTADO, aportar(10, 25, LUNES).estado());
    }

    @Test
    void laClaveYaUsada_esRepetido() {
        when(registroService.completar(any(), any())).thenReturn(respuestaDeCompletar(true));
        ResultadoAporte resultado = aportar(10, 25, HOY);
        assertEquals(ResultadoAporte.Estado.REPETIDO, resultado.estado());
        assertEquals(0, resultado.monedas());
    }

    @Test
    void conLaClaveYaUsada_vaACompletarAunqueAhoraEsteEnPausa() {
        // Como completar: primero la clave. Un reintento responde lo mismo
        // aunque desde entonces se haya pausado el hábito.
        leer.setActivo(false);
        when(registroDAO.findByClave(CLAVE)).thenReturn(new Registro());
        when(registroService.completar(any(), any())).thenReturn(respuestaDeCompletar(true));
        assertEquals(ResultadoAporte.Estado.REPETIDO, aportar(10, 25, HOY).estado());
    }

    // ── Rechazos, sin llamar a completar ─────────────────────────

    @Test
    void rechaza_enPausa() {
        leer.setActivo(false);
        rechazado(aportar(10, 25, HOY), DestinoMinutosHabitos.MOTIVO_PAUSADO);
    }

    @Test
    void rechaza_inexistente_yAjeno_conElMismoMotivo() {
        rechazado(aportar(99, 25, HOY), DestinoMinutosHabitos.MOTIVO_NO_ENCONTRADO);
        Usuario otro = new Usuario();
        otro.setUsuarioId(8);
        leer.setPropietario(otro);
        rechazado(aportar(10, 25, HOY), DestinoMinutosHabitos.MOTIVO_NO_ENCONTRADO);
        rechazado(destino.aportar(7L, null, 25, CLAVE, HOY, "ENFOQUE"), DestinoMinutosHabitos.MOTIVO_NO_ENCONTRADO);
        rechazado(destino.aportar(7L, 1L + Integer.MAX_VALUE, 25, CLAVE, HOY, "ENFOQUE"),
                DestinoMinutosHabitos.MOTIVO_NO_ENCONTRADO);
    }

    @Test
    void rechaza_deVeces() {
        Habito meditar = habito(11, "Meditar", new Objetivo(Frecuencia.DIARIO, 1, null));
        when(habitoDAO.findById(11)).thenReturn(meditar);
        rechazado(aportar(11, 25, HOY), DestinoMinutosHabitos.MOTIVO_NO_MINUTOS);
    }

    @Test
    void rechaza_deVecesEseDia_aunqueHoySeaDeMinutos() {
        // Pasó de veces a minutos el miércoles: el lunes era de veces.
        Objetivo veces = new Objetivo(Frecuencia.DIARIO, 1, null);
        when(objetivoService.historiales(anyList())).thenReturn(Map.of(10, HistorialObjetivos.de(leer, List.of(
                new HabitoObjetivo(leer, LocalDate.of(2026, 1, 1), veces),
                new HabitoObjetivo(leer, LUNES.plusDays(2), Objetivo.de(leer))))));
        rechazado(aportar(10, 25, LUNES), DestinoMinutosHabitos.MOTIVO_NO_MINUTOS);
    }

    @Test
    void rechaza_semanaPasada_elDomingoVistoElLunes() {
        when(rachaService.hoyDe(any(Habito.class))).thenReturn(LocalDate.of(2026, 10, 12));
        rechazado(aportar(10, 25, LocalDate.of(2026, 10, 11)), DestinoMinutosHabitos.MOTIVO_SEMANA_PASADA);
    }

    @Test
    void rechaza_diaFuturo() {
        rechazado(aportar(10, 25, HOY.plusDays(1)), DestinoMinutosHabitos.MOTIVO_DIA_FUTURO);
        rechazado(aportar(10, 25, null), DestinoMinutosHabitos.MOTIVO_DIA_FUTURO);
    }

    @Test
    void rechaza_minutosFueraDeRango() {
        rechazado(aportar(10, 0, HOY), DestinoMinutosHabitos.MOTIVO_MINUTOS_RANGO);
        rechazado(aportar(10, 601, HOY), DestinoMinutosHabitos.MOTIVO_MINUTOS_RANGO);
    }

    @Test
    void rechaza_origenDesconocido() {
        rechazado(destino.aportar(7L, 10L, 25, CLAVE, HOY, "OTRO"), DestinoMinutosHabitos.MOTIVO_ORIGEN);
        rechazado(destino.aportar(7L, 10L, 25, CLAVE, HOY, null), DestinoMinutosHabitos.MOTIVO_ORIGEN);
    }

    // ── Transacción ──────────────────────────────────────────────

    @Test
    void aporta_enSuPropiaTransaccion_REQUIRES_NEW() {
        when(registroService.completar(any(), any())).thenReturn(respuestaDeCompletar(false));
        aportar(10, 25, HOY);
        ArgumentCaptor<TransactionDefinition> definicion = ArgumentCaptor.forClass(TransactionDefinition.class);
        verify(transactionManager).getTransaction(definicion.capture());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, definicion.getValue().getPropagationBehavior());
        verify(transactionManager).commit(estadoTx);
    }

    @Test
    void unaExcepcionDeNegocioDeCompletar_noEscapa_yDeshaceSoloSuTransaccion() {
        when(registroService.completar(any(), any())).thenThrow(new ConflictoException("El hábito está en pausa"));

        ResultadoAporte resultado = aportar(10, 25, HOY);

        assertEquals(ResultadoAporte.Estado.RECHAZADO, resultado.estado());
        assertEquals(DestinoMinutosHabitos.MOTIVO_CONFLICTO, resultado.motivo());
        verify(transactionManager).rollback(estadoTx);
        verify(transactionManager, never()).commit(any());
    }

    @Test
    void unIllegalArgumentDeCompletar_tampocoEscapa() {
        when(registroService.completar(any(), any())).thenThrow(new IllegalArgumentException("minutos"));
        assertEquals(DestinoMinutosHabitos.MOTIVO_INVALIDO, aportar(10, 25, HOY).motivo());
    }

    @Test
    void unFalloTecnico_siSale_paraQueSeReintente() {
        when(registroService.completar(any(), any()))
                .thenThrow(new DataAccessResourceFailureException("sin conexión"));
        assertThrows(DataAccessResourceFailureException.class, () -> aportar(10, 25, HOY));
        verify(transactionManager).rollback(estadoTx);
    }

    // ── Disponibles ──────────────────────────────────────────────

    @Test
    void disponibles_losDeMinutosActivos_conLoQueLlevanHoy() {
        Habito meditar = habito(11, "Meditar", new Objetivo(Frecuencia.DIARIO, 1, null));
        when(usuarioDAO.findById(7)).thenReturn(usuario);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(leer, meditar));
        Registro r1 = new Registro(leer, true, null, HOY);
        r1.setCantidad(10);
        Registro r2 = new Registro(leer, true, null, HOY);
        r2.setCantidad(15);
        when(registroDAO.findByHabitoAndRango(leer, HOY, HOY)).thenReturn(List.of(r1, r2));

        List<DestinoDisponible> disponibles = destino.disponibles(7L);

        assertEquals(List.of(new DestinoDisponible("habito", 10L, "Leer", 25, 30)), disponibles);
        verify(objetivoService, never()).ponerAlDia(any(), any());
    }

    @Test
    void losMotivos_cabenEnLaColumna() {
        for (String motivo : DestinoMinutosHabitos.MOTIVOS) {
            assertTrue(motivo.length() <= 40, motivo);
        }
    }
}
