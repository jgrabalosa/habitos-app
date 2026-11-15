package com.norday.habitos;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.service.TextosService;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.LogrosHabitosService;
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

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El completado con clave del cliente: un reintento no crea nada ni paga
 * nada, tampoco cuando los dos llegan a la vez. Y el completado de minutos
 * pide los minutos.
 */
@ExtendWith(MockitoExtension.class)
class CompletarConClaveTest {

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");
    private static final LocalDate HOY = LocalDate.of(2026, 10, 9);
    private static final UUID CLAVE = UUID.fromString("3f1c6a52-6a0e-4c43-9a59-0b4f3c7c2b11");

    @Mock private IRegistroDAO registroDAO;
    @Mock private IRachaDAO rachaDAO;
    @Mock private LogrosHabitosService logrosHabitosService;
    @Mock private UsuarioMonedaService usuarioMonedaService;
    @Mock private MascotaService mascotaService;
    @Mock private RachaService rachaService;
    @Mock private HabitoService habitoService;
    @Mock private ILogroDAO logroDAO;
    @Mock private IUsuarioLogroDAO usuarioLogroDAO;
    @Mock private IReversionRegistroDAO reversionRegistroDAO;
    @Mock private IHitoPagadoDAO hitoPagadoDAO;
    @Mock private ObjetivoService objetivoService;
    @Mock private TextosService textosService;

    @InjectMocks
    private RegistroService registroService;

    private Usuario usuario;
    private Habito habito;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        habito = new Habito();
        habito.setActivo(true);
        habito.setHabitoId(10);
        // Bloquear devuelve el hábito releído; aquí, el mismo.
        lenient().when(habitoService.bloquear(any(Habito.class))).thenAnswer(i -> i.getArgument(0));
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setUnidad(Unidad.MINUTOS);
        habito.setMinutosObjetivo(30);
        habito.setPropietario(usuario);

        lenient().when(rachaService.hoyDe(any(Habito.class))).thenReturn(HOY);
        lenient().when(rachaService.zonaDe(any(Habito.class))).thenReturn(ZONA);
        lenient().when(mascotaService.obtenerOCrear(anyInt())).thenReturn(new Mascota(usuario));
        lenient().when(textosService.texto(anyString(), any(Usuario.class), any())).thenReturn("texto");
        lenient().when(textosService.texto(anyString(), any(Usuario.class))).thenReturn("texto");
    }

    private Registro original(int minutos) {
        Registro registro = new Registro(habito, true, null, HOY);
        registro.setRegistroId(77);
        registro.setCantidad(minutos);
        registro.setClaveCliente(CLAVE);
        return registro;
    }

    private void sinNadaPagado() {
        verify(usuarioMonedaService, never()).registrarMovimiento(any(), anyInt(), anyString(), anyInt(), anyString());
        verify(mascotaService, never()).ganarExperiencia(anyInt(), anyInt());
        verify(hitoPagadoDAO, never()).apuntar(any(), anyInt(), any());
        verify(reversionRegistroDAO, never()).save(any());
        verify(logrosHabitosService, never()).evaluarTrasCompletarRegistro(any(), any());
    }

    @Test
    void unaClaveYaUsada_devuelveElOriginalSinCrearNiPagarNada() {
        Registro original = original(20);
        when(registroDAO.findByClave(CLAVE)).thenReturn(original);
        when(registroDAO.findByHabitoAndRango(eq(habito), eq(HOY), eq(HOY))).thenReturn(List.of(original));

        Map<String, Object> r = registroService.completar(habito,
                new Solicitud(null, null, "20", CLAVE, OrigenRegistro.MANUAL));

        assertEquals(true, r.get("repetido"));
        assertEquals(77, r.get("registroId"));
        assertEquals(0, r.get("puntosGanados"));
        assertEquals(0, r.get("puntosPaso"));
        assertEquals(0, r.get("xpGanada"));
        assertEquals(List.of(), r.get("logros"));
        assertEquals(false, r.get("mostrarValoracion"));
        assertEquals(20, r.get("minutosDia"));
        assertEquals(30, r.get("minutosObjetivo"));
        assertEquals(false, r.get("diaHecho"));
        verify(registroDAO, never()).save(any());
        verify(registroDAO, never()).guardarSiClaveLibre(any());
        verify(habitoService, never()).bloquear(any());
        sinNadaPagado();
    }

    @Test
    void laClaveSeVuelveAMirarTrasElBloqueo() {
        // Antes del bloqueo no estaba; tras él, el otro completado ya la ha
        // confirmado: se responde como un reintento, sin intentar guardar.
        Registro original = original(20);
        when(registroDAO.findByClave(CLAVE)).thenReturn(null, original);
        when(registroDAO.findByHabitoAndRango(eq(habito), eq(HOY), eq(HOY))).thenReturn(List.of(original));

        Map<String, Object> r = registroService.completar(habito,
                new Solicitud(null, null, "20", CLAVE, OrigenRegistro.MANUAL));

        org.mockito.InOrder orden = inOrder(registroDAO, habitoService);
        orden.verify(registroDAO).findByClave(CLAVE);
        orden.verify(habitoService).bloquear(habito);
        orden.verify(registroDAO).findByClave(CLAVE);
        assertEquals(true, r.get("repetido"));
        verify(registroDAO, never()).guardarSiClaveLibre(any());
        sinNadaPagado();
    }

    @Test
    void dosALaVez_elSegundoChocaConLaClaveYDevuelveElOriginal() {
        // Cuando este mira, la clave aún no existe; al guardar, el otro ya la
        // ha escrito: ON CONFLICT no escribe nada y devuelve null.
        Registro original = original(20);
        // Tres miradas: antes del bloqueo, tras el bloqueo y tras chocar.
        when(registroDAO.findByClave(CLAVE)).thenReturn(null, null, original);
        when(registroDAO.guardarSiClaveLibre(any(Registro.class))).thenReturn(null);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any())).thenReturn(new ArrayList<>(),
                List.of(original));

        Map<String, Object> r = registroService.completar(habito,
                new Solicitud(null, null, "20", CLAVE, OrigenRegistro.MANUAL));

        assertEquals(true, r.get("repetido"));
        assertEquals(77, r.get("registroId"));
        assertEquals(0, r.get("puntosGanados"));
        assertEquals(20, r.get("minutosDia"));
        verify(registroDAO, never()).save(any());
        sinNadaPagado();
    }

    @Test
    void conClaveNueva_seGuardaConSuClaveSusMinutosYSuOrigen() {
        when(registroDAO.findByClave(CLAVE)).thenReturn(null);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any())).thenReturn(new ArrayList<>());
        when(registroDAO.guardarSiClaveLibre(any(Registro.class))).thenAnswer(inv -> {
            Registro guardado = inv.getArgument(0);
            guardado.setRegistroId(78);
            return guardado;
        });
        when(logrosHabitosService.evaluarTrasCompletarRegistro(any(), any())).thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt())).thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> r = registroService.completar(habito,
                new Solicitud(null, null, "15", CLAVE, OrigenRegistro.ENFOQUE));

        ArgumentCaptor<Registro> captor = ArgumentCaptor.forClass(Registro.class);
        verify(registroDAO).guardarSiClaveLibre(captor.capture());
        assertEquals(15, captor.getValue().getCantidad());
        assertEquals(OrigenRegistro.ENFOQUE, captor.getValue().getOrigen());
        assertEquals(CLAVE, captor.getValue().getClaveCliente());
        assertEquals(false, r.get("repetido"));
        assertEquals(78, r.get("registroId"));
        assertEquals(12, r.get("puntosPaso"));
        assertEquals(2, r.get("xpGanada"));
        assertEquals(15, r.get("minutosDia"));
        assertEquals(false, r.get("diaHecho"));
    }

    @Test
    void unaClaveDeOtroHabito_esUnConflicto() {
        Habito otro = new Habito();
        otro.setHabitoId(99);
        Registro ajeno = new Registro(otro, true, null, HOY);
        ajeno.setClaveCliente(CLAVE);
        when(registroDAO.findByClave(CLAVE)).thenReturn(ajeno);

        assertThrows(ConflictoException.class, () -> registroService.completar(habito,
                new Solicitud(null, null, "15", CLAVE, OrigenRegistro.MANUAL)));
        sinNadaPagado();
    }

    @Test
    void minutosSinMinutos_esUnConflicto_yNuncaUnMinutoPorDefecto() {
        ConflictoException e = assertThrows(ConflictoException.class, () -> registroService.completar(habito,
                new Solicitud(null, null, null, null, OrigenRegistro.MANUAL)));
        assertEquals("texto", e.getMessage());
        verify(textosService).texto("registro.minutos.actualizar.app", usuario);
        // Por la puerta de la app ya instalada, igual.
        assertThrows(ConflictoException.class, () -> registroService.completarHabito(habito, "nota"));
        verify(registroDAO, never()).save(any());
        sinNadaPagado();
    }

    @Test
    void minutosFueraDeRango_esUnaPeticionMala() {
        for (String minutos : List.of("0", "601", "-5", "abc", "1.5")) {
            assertThrows(IllegalArgumentException.class, () -> registroService.completar(habito,
                    new Solicitud(null, null, minutos, null, OrigenRegistro.MANUAL)), minutos);
        }
        verify(textosService, atLeastOnce()).texto("registro.minutos.rango", usuario, 1, 600);
        verify(registroDAO, never()).save(any());
        sinNadaPagado();
    }

    @Test
    void enVeces_losMinutosSeIgnoran_yElRegistroVale1() {
        habito.setUnidad(Unidad.VECES);
        habito.setMinutosObjetivo(null);
        when(registroDAO.findByHabitoAndRango(eq(habito), any(), any())).thenReturn(new ArrayList<>());
        when(logrosHabitosService.evaluarTrasCompletarRegistro(any(), any())).thenReturn(new ArrayList<>());
        when(mascotaService.ganarExperiencia(anyInt(), anyInt())).thenReturn(new ResultadoExperienciaDTO(false, 1, null));

        Map<String, Object> r = registroService.completar(habito,
                new Solicitud(null, null, "abc", null, OrigenRegistro.MANUAL));

        ArgumentCaptor<Registro> captor = ArgumentCaptor.forClass(Registro.class);
        verify(registroDAO).save(captor.capture());
        assertEquals(1, captor.getValue().getCantidad());
        assertEquals(25, r.get("puntosPaso"));
        assertNull(r.get("minutosDia"));
        assertNull(r.get("minutosObjetivo"));
        assertEquals(true, r.get("diaHecho"));
    }
}
