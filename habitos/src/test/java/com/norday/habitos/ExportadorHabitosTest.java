package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HitoPagado;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.model.AvisoApp;
import com.norday.habitos.model.TipoAviso;
import com.norday.habitos.repository.IAvisoAppDAO;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import com.norday.habitos.service.ExportadorHabitos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ExportadorHabitosTest {

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @Mock
    private IRitmoDAO ritmoDAO;

    @Mock
    private ICategoriaDAO categoriaDAO;

    @Mock
    private IHabitoObjetivoDAO objetivoDAO;

    @Mock
    private IHitoPagadoDAO hitoPagadoDAO;

    @Mock
    private IAvisoAppDAO avisoAppDAO;

    @Mock
    private com.norday.habitos.service.ObjetivoService objetivoService;

    @Mock
    private com.norday.core.service.ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private ExportadorHabitos exportador;

    // Desde la Fase 3 el exportador pone al día el objetivo con el "hoy" del
    // usuario antes de exportar.
    @org.junit.jupiter.api.BeforeEach
    void zonaDelUsuario() {
        lenient().when(zonaUsuarioService.zonaDe(any(Usuario.class)))
                .thenReturn(java.time.ZoneId.of("Europe/Madrid"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportar_exportaElObjetivoVigenteHoySinEscribir_yLlevaUnidadMinutosCantidadYOrigen() {
        // La exportación corre en una transacción de solo lectura: el
        // objetivo de hoy se calcula con el historial, sin poner al día (sin
        // escribir) la copia del hábito.
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.of(2026, 9, 1));
        habito.setHabitoId(5);
        habito.setUnidad(com.norday.habitos.model.Unidad.MINUTOS);
        habito.setMinutosObjetivo(30);
        // Un cambio a 45 minutos que ya ha llegado y la copia no tiene aún.
        HabitoObjetivo primera = new HabitoObjetivo(habito, LocalDate.of(2026, 9, 1),
                new Objetivo(com.norday.habitos.model.Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null));
        HabitoObjetivo llegada = new HabitoObjetivo(habito, LocalDate.of(2026, 10, 1),
                new Objetivo(com.norday.habitos.model.Unidad.MINUTOS, Frecuencia.DIARIO, 1, 45, null));
        Registro registro = new Registro(habito, true, null, LocalDate.of(2026, 9, 2));
        registro.setCantidad(25);
        registro.setOrigen(com.norday.habitos.model.OrigenRegistro.ENFOQUE);
        registro.setClaveCliente(java.util.UUID.randomUUID());

        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(habito));
        when(objetivoService.historiales(List.of(habito))).thenReturn(Map.of(5,
                com.norday.habitos.model.HistorialObjetivos.de(habito, List.of(primera, llegada))));
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());

        Map<String, Object> datos = exportador.exportar(usuario);

        verify(objetivoService, never()).ponerAlDia(any(), any());
        Map<String, Object> exportado = ((List<Map<String, Object>>) datos.get("habitos")).get(0);
        assertEquals("MINUTOS", exportado.get("unidad"));
        assertEquals(45, exportado.get("minutosObjetivo"), "el objetivo vigente hoy, no la copia vieja");
        assertEquals(30, habito.getMinutosObjetivo(), "la copia no se ha tocado");
        Map<String, Object> reg = ((List<Map<String, Object>>) exportado.get("registros")).get(0);
        assertEquals(25, reg.get("cantidad"));
        assertEquals("ENFOQUE", reg.get("origen"));
        assertFalse(reg.containsKey("claveCliente"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportar_incluyeFechaCreacionDelHabitoYCreadoEnDelRegistro() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.of(2026, 9, 1));
        // La entidad no tiene setter de fechaCreacion a propósito.
        ReflectionTestUtils.setField(habito, "fechaCreacion",
                LocalDateTime.of(2026, 9, 1, 7, 45, 0));

        Registro registro = new Registro(habito, true, null, LocalDate.of(2026, 9, 2));
        ReflectionTestUtils.setField(registro, "creadoEn",
                LocalDateTime.of(2026, 9, 2, 21, 5, 0));

        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(habito));
        when(rachaDAO.findByHabito(habito)).thenReturn(null);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());

        Map<String, Object> datos = exportador.exportar(usuario);

        List<Map<String, Object>> habitos = (List<Map<String, Object>>) datos.get("habitos");
        assertEquals(1, habitos.size());
        assertEquals("2026-09-01T07:45", habitos.get(0).get("fechaCreacion"));

        List<Map<String, Object>> registros =
                (List<Map<String, Object>>) habitos.get(0).get("registros");
        assertEquals(1, registros.size());
        assertEquals("2026-09-02T21:05", registros.get(0).get("creadoEn"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportar_incluyeElRitmoDeCadaHabito() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.of(2026, 9, 1));
        ReflectionTestUtils.setField(habito, "fechaCreacion",
                LocalDateTime.of(2026, 9, 1, 7, 45, 0));

        Ritmo ritmo = new Ritmo(habito, LocalDate.of(2026, 10, 5));
        ritmo.setValor(0.75);
        ritmo.setValorPrevio(0.6);
        ritmo.setDesdePrevio(LocalDate.of(2026, 9, 28));
        ritmo.setCambio(LocalDate.of(2026, 10, 1));

        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(habito));
        when(ritmoDAO.findByHabito(habito)).thenReturn(ritmo);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of());
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());

        Map<String, Object> datos = exportador.exportar(usuario);

        List<Map<String, Object>> habitos = (List<Map<String, Object>>) datos.get("habitos");
        Map<String, Object> exportado = (Map<String, Object>) habitos.get(0).get("ritmo");
        assertEquals(0.75, exportado.get("valor"));
        assertEquals("2026-10-05", exportado.get("desde"));
        assertEquals(0.6, exportado.get("valorPrevio"));
        assertEquals("2026-09-28", exportado.get("desdePrevio"));
        assertEquals("2026-10-01", exportado.get("cambio"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportar_incluyeElHistorialDeObjetivosYLosHitosCobrados() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Habito habito = new Habito("Leer", null, Frecuencia.SEMANAL, 3,
                usuario, null, LocalDate.of(2026, 9, 1));
        habito.setDiasSemana("1,3,5");
        HabitoObjetivo primera = new HabitoObjetivo(habito, LocalDate.of(2026, 9, 1),
                new Objetivo(Frecuencia.DIARIO, 1, null));
        HabitoObjetivo programada = new HabitoObjetivo(habito, LocalDate.of(2026, 10, 12),
                new Objetivo(Frecuencia.SEMANAL, 3, "1,3,5"));
        HitoPagado hito = new HitoPagado(habito, 3, null);
        ReflectionTestUtils.setField(hito, "pagadoEn", LocalDateTime.of(2026, 9, 3, 20, 0, 0));

        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(habito));
        when(objetivoDAO.findByHabito(habito)).thenReturn(List.of(primera, programada));
        when(hitoPagadoDAO.findByHabito(habito)).thenReturn(List.of(hito));
        when(registroDAO.findByHabito(habito)).thenReturn(List.of());
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());

        Map<String, Object> datos = exportador.exportar(usuario);

        Map<String, Object> exportado =
                ((List<Map<String, Object>>) datos.get("habitos")).get(0);
        List<Map<String, Object>> objetivos = (List<Map<String, Object>>) exportado.get("objetivos");
        assertEquals(2, objetivos.size());
        assertEquals(Map.of("desde", "2026-09-01", "unidad", "VECES", "frecuencia", "DIARIO",
                "meta", 1), sinNulos(objetivos.get(0)));
        assertTrue(objetivos.get(0).containsKey("minutosObjetivo"));
        assertNull(objetivos.get(0).get("minutosObjetivo"));
        assertNull(objetivos.get(0).get("diasSemana"));
        assertEquals("2026-10-12", objetivos.get(1).get("desde"));
        assertEquals("SEMANAL", objetivos.get(1).get("frecuencia"));
        assertEquals(3, objetivos.get(1).get("meta"));
        assertEquals("1,3,5", objetivos.get(1).get("diasSemana"));

        List<Map<String, Object>> hitos = (List<Map<String, Object>>) exportado.get("hitosPagados");
        assertEquals(List.of(Map.of("umbral", 3, "pagadoEn", "2026-09-03T20:00")), hitos);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportar_incluyeLosAvisosDeLaAppDelUsuario() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(7);
        AvisoApp aviso = new AvisoApp(usuario, TipoAviso.MOMENTO_DEBIL, 2);
        aviso.setEnviadoEn(LocalDateTime.of(2026, 11, 3, 18, 30, 0));

        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of());
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());
        when(avisoAppDAO.findByUsuario(7)).thenReturn(List.of(aviso));

        Map<String, Object> datos = exportador.exportar(usuario);

        List<Map<String, Object>> avisos = (List<Map<String, Object>>) datos.get("avisosApp");
        assertEquals(List.of(Map.of("tipo", "MOMENTO_DEBIL", "variante", 2,
                "enviadoEn", "2026-11-03T18:30")), avisos);
    }

    @Test
    @SuppressWarnings("unchecked")
    void exportar_sinAvisos_dejaLaListaVacia() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(7);
        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of());
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());

        Map<String, Object> datos = exportador.exportar(usuario);

        assertEquals(List.of(), datos.get("avisosApp"));
    }

    private static Map<String, Object> sinNulos(Map<String, Object> mapa) {
        Map<String, Object> copia = new java.util.HashMap<>();
        mapa.forEach((clave, valor) -> {
            if (valor != null) copia.put(clave, valor);
        });
        return copia;
    }
}
