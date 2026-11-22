package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Unidad;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.CumplimientoDiarioHabitos;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.ObjetivoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.norday.gamificacion.model.AvanceDeHoy.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Cómo irá el usuario los próximos días si no hace nada, para el ánimo de
 * Nori sin abrir la app. La regla es la de ComoVaHoyTest, con otro día: aquí
 * se comprueba que cada día futuro sale como saldría «hoy» ese día con los
 * mismos datos. Los datos que no cambian son los registros hasta hoy (el
 * registroDAO de mentira devuelve lo que cae en el rango pedido).
 *
 * "Hoy" es el sábado 10-oct-2026 salvo donde se dice; el lunes de esa semana,
 * el 5. Los siete días siguientes son domingo 11 a sábado 17.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ComoVaLosProximosDiasTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    @Mock private HabitoService habitoService;
    @Mock private IUsuarioDAO usuarioDAO;
    @Mock private IHabitoDAO habitoDAO;
    @Mock private IRegistroDAO registroDAO;
    @Mock private ZonaUsuarioService zonaUsuarioService;
    @Mock private ObjetivoService objetivoService;

    @InjectMocks
    private CumplimientoDiarioHabitos cumplimiento;

    private Usuario usuario;
    private final List<Registro> registros = new ArrayList<>();
    private final Map<Integer, HistorialObjetivos> historiales = new HashMap<>();

    private Habito habito(int id, Frecuencia frecuencia, int meta, String diasSemana) {
        Habito habito = new Habito();
        habito.setHabitoId(id);
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setPropietario(usuario);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
        return habito;
    }

    private Habito deMinutos(int id, int minutos) {
        Habito habito = habito(id, Frecuencia.DIARIO, 1, null);
        habito.setUnidad(Unidad.MINUTOS);
        habito.setMinutosObjetivo(minutos);
        return habito;
    }

    private Registro hecho(Habito habito, LocalDate fecha) {
        Registro registro = new Registro(habito, true, "", fecha);
        registros.add(registro);
        return registro;
    }

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        // Alta hace mucho: no es un recién llegado.
        usuario.setFechaRegistro(LocalDateTime.of(2026, 1, 1, 10, 0));
        // Como la consulta de verdad: solo lo que cae en el rango pedido.
        when(registroDAO.findByHabitosAndRango(any(), any(), any())).thenAnswer(invocacion -> {
            LocalDate desde = invocacion.getArgument(1);
            LocalDate hasta = invocacion.getArgument(2);
            return registros.stream()
                    .filter(r -> !r.getFecha().isBefore(desde) && !r.getFecha().isAfter(hasta))
                    .toList();
        });
        when(objetivoService.historiales(any())).thenReturn(historiales);
    }

    private void activos(Habito... habitos) {
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(habitos));
        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(habitos));
    }

    private List<AvanceDeHoy> proximos(LocalDate hoy, int dias) {
        return cumplimiento.comoVaLosProximosDias(usuario, hoy, MADRID, dias);
    }

    /** Cada día futuro es lo que daría comoVaHoy ese día con los mismos datos. */
    private void assertCadaDiaEsComoVaHoy(LocalDate hoy, int dias) {
        List<AvanceDeHoy> previstos = proximos(hoy, dias);
        assertEquals(dias, previstos.size());
        for (int i = 1; i <= dias; i++) {
            LocalDate dia = hoy.plusDays(i);
            assertEquals(cumplimiento.comoVaHoy(usuario, dia, MADRID), previstos.get(i - 1), "día " + dia);
        }
    }

    @Test
    void diarios_duermeLosDiasSiguientes_yEstaAusenteCuandoToca() {
        Habito leer = habito(1, Frecuencia.DIARIO, 1, null);
        activos(leer);
        hecho(leer, HOY);

        // Hecho hoy, sábado: domingo, lunes y martes llevan 0, 1 y 2 días sin
        // nada; el miércoles 14, tres.
        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, AUSENTE, AUSENTE, AUSENTE, AUSENTE),
                proximos(HOY, 7));
    }

    @Test
    void soloSemanales_siguenEmpezadosHastaElDomingo_yElLunesEmpiezaDeCero() {
        Habito gimnasio = habito(3, Frecuencia.SEMANAL, 5, null);
        activos(gimnasio);
        hecho(gimnasio, LUNES);

        // Una sesión el lunes 5: el domingo 11 sigue siendo de esa semana; del
        // lunes 12 en adelante la semana empieza de cero (y la anterior, con
        // sesión, impide el ausente).
        assertEquals(List.of(EMPEZADO, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR),
                proximos(HOY, 7));
    }

    @Test
    void soloSemanales_sinNadaEstaSemana_ausenteElLunesSiLaPasadaTampocoTuvo() {
        Habito gimnasio = habito(3, Frecuencia.SEMANAL, 5, null);
        activos(gimnasio);
        hecho(gimnasio, LocalDate.of(2026, 9, 28));

        // Última sesión el lunes 28-sep: el domingo 11 aún es «la semana
        // pasada» de esa; el lunes 12 ya pasó una semana entera sin sesión.
        assertEquals(List.of(SIN_EMPEZAR, AUSENTE, AUSENTE), proximos(HOY, 3));
    }

    @Test
    void todoEnPausa_siempreSinEmpezar_nuncaAusente() {
        Habito leer = habito(1, Frecuencia.DIARIO, 1, null);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());
        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(leer));

        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR),
                proximos(HOY, 7));
    }

    @Test
    void recienRegistradoSinNadaHecho_cuentaDesdeElAlta() {
        Habito leer = habito(1, Frecuencia.DIARIO, 1, null);
        activos(leer);
        // Alta el viernes 9: el domingo lleva 1 día entero sin nada, el lunes
        // 2 y el martes 3.
        usuario.setFechaRegistro(LocalDateTime.of(2026, 10, 9, 10, 0));

        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, AUSENTE, AUSENTE), proximos(HOY, 4));
    }

    @Test
    void sinHabitosNiRegistros_cuentaDesdeElAlta() {
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());
        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of());
        usuario.setFechaRegistro(LocalDateTime.of(2026, 10, 9, 10, 0));

        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, AUSENTE), proximos(HOY, 3));
    }

    @Test
    void conDatosVariados_cadaDiaEsLoQueDariaComoVaHoyEseDia() {
        // Hoy, miércoles 7-oct: la semana tiene cuatro días futuros (jueves a
        // domingo) y luego empieza otra.
        Habito leer = habito(1, Frecuencia.DIARIO, 1, null);
        Habito agua = habito(2, Frecuencia.DIARIO, 4, null);
        Habito gimnasio = habito(3, Frecuencia.SEMANAL, 3, null);
        Habito yoga = habito(4, Frecuencia.SEMANAL, 3, "2,4,6");
        Habito correr = deMinutos(5, 30);
        activos(leer, agua, gimnasio, yoga, correr);

        hecho(leer, LUNES);
        hecho(agua, MARTES);
        hecho(agua, MARTES);
        hecho(gimnasio, LUNES);
        hecho(yoga, LUNES);
        hecho(yoga, MARTES);
        // Un hábito de minutos a medias hoy: 20 de 30. Es lo único de hoy.
        hecho(correr, MIERCOLES).setCantidad(20);

        // Cambio de objetivo del yoga programado para pasado mañana (viernes 9):
        // pasa a tocar también los viernes.
        LocalDate viernes = LocalDate.of(2026, 10, 9);
        Habito nuevo = habito(4, Frecuencia.SEMANAL, 3, "2,4,5,6");
        historiales.put(4, HistorialObjetivos.de(yoga, List.of(
                new HabitoObjetivo(yoga, LocalDate.of(2026, 1, 1), Objetivo.de(yoga)),
                new HabitoObjetivo(yoga, viernes, Objetivo.de(nuevo)))));

        assertCadaDiaEsComoVaHoy(MIERCOLES, 7);
        // Y no es una lista cualquiera: los 20 minutos de hoy cuentan como
        // actividad (sin ellos, el sábado ya sería el tercer día sin nada
        // desde el martes), y el domingo es el tercer día desde hoy.
        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR, SIN_EMPEZAR, AUSENTE, AUSENTE, AUSENTE, AUSENTE),
                proximos(MIERCOLES, 7));
    }

    @Test
    void unCambioDeObjetivoProgramado_cuentaDesdeSuFecha() {
        // Hoy, miércoles 7: tres sesiones de yoga (lunes, martes y hoy) de un
        // semanal con días fijos martes, jueves y sábado.
        Habito yoga = habito(4, Frecuencia.SEMANAL, 3, "2,4,6");
        activos(yoga);
        hecho(yoga, LUNES);
        hecho(yoga, MARTES);
        hecho(yoga, MIERCOLES);

        // Sin cambio: el jueves toca y está cubierto (3 sesiones, 2 días
        // tocaban hasta él); el viernes no toca; el sábado toca y está
        // cubierto (3 y 3).
        assertEquals(List.of(COMPLETO, EMPEZADO, COMPLETO, EMPEZADO), proximos(MIERCOLES, 4));

        // Con un cambio desde el viernes 9 que añade los viernes: el viernes
        // toca (3 días tocaban, 3 sesiones: cubierto) y el sábado, con cuatro
        // días tocando, ya no. El jueves y el domingo, igual que antes.
        LocalDate viernes = LocalDate.of(2026, 10, 9);
        Habito nuevo = habito(4, Frecuencia.SEMANAL, 3, "2,4,5,6");
        historiales.put(4, HistorialObjetivos.de(yoga, List.of(
                new HabitoObjetivo(yoga, LocalDate.of(2026, 1, 1), Objetivo.de(yoga)),
                new HabitoObjetivo(yoga, viernes, Objetivo.de(nuevo)))));
        assertEquals(List.of(COMPLETO, COMPLETO, EMPEZADO, EMPEZADO), proximos(MIERCOLES, 4));
        assertCadaDiaEsComoVaHoy(MIERCOLES, 7);
    }

    @Test
    void conCeroDias_listaVacia_ySinUsuario_sinEmpezar() {
        assertEquals(List.of(), cumplimiento.comoVaLosProximosDias(1, 0));
        when(usuarioDAO.findById(9)).thenReturn(null);
        assertEquals(List.of(SIN_EMPEZAR, SIN_EMPEZAR), cumplimiento.comoVaLosProximosDias(9, 2));
    }
}
