package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Tendencia;
import com.norday.habitos.model.dto.FilaHoyDTO;
import com.norday.habitos.model.dto.HoyDTO;
import com.norday.habitos.model.dto.SeccionHoyDTO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.HoyService;
import com.norday.habitos.service.LogrosHabitosService;
import com.norday.habitos.service.RitmoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * El caso base es la lámina aprobada de Hoy: sábado 10-oct-2026, con agua
 * 3/4, leer 0/1, yoga (martes, jueves y sábado) con una sesión el martes,
 * meditar hecho, gimnasio 2/5 y llamar a casa cumplido.
 *
 * Los hábitos van por orden de nombre, que es como los devuelve el DAO.
 */
@ExtendWith(MockitoExtension.class)
class HoyServiceTest {

    private static final double D = 0.000001;
    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate SABADO = LocalDate.of(2026, 10, 10);
    private static final LocalDate DOMINGO = LocalDate.of(2026, 10, 11);

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private RitmoService ritmoService;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @Mock
    private LogrosHabitosService logrosHabitosService;

    @InjectMocks
    private HoyService hoyService;

    private Usuario usuario;
    private Habito agua;
    private Habito gimnasio;
    private Habito leer;
    private Habito llamar;
    private Habito meditar;
    private Habito yoga;
    private List<Habito> activos;

    private Habito habito(int id, String nombre, Frecuencia frecuencia, int meta, String diasSemana) {
        Habito habito = new Habito();
        habito.setHabitoId(id);
        habito.setNombre(nombre);
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setPropietario(usuario);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
        return habito;
    }

    private Registro hecho(Habito quien, LocalDate fecha) {
        return new Registro(quien, true, "", fecha);
    }

    /** Un ritmo que no se ha movido en la semana. */
    private RitmoLeido leido(double valor) {
        return new RitmoLeido(valor, valor);
    }

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);

        agua = habito(1, "Beber agua", Frecuencia.DIARIO, 4, null);
        leer = habito(2, "Leer 20 minutos", Frecuencia.DIARIO, 1, null);
        yoga = habito(3, "Yoga", Frecuencia.SEMANAL, 3, "2,4,6");
        meditar = habito(4, "Meditar", Frecuencia.DIARIO, 1, null);
        gimnasio = habito(5, "Gimnasio", Frecuencia.SEMANAL, 5, null);
        llamar = habito(6, "Llamar a casa", Frecuencia.SEMANAL, 1, null);
        activos = List.of(agua, gimnasio, leer, llamar, meditar, yoga);
    }

    /** La semana de la lámina, con "hoy" en el día dado. */
    private void laSemanaDeLaMaqueta(LocalDate hoy) {
        when(habitoDAO.findActivos(usuario)).thenReturn(activos);
        when(registroDAO.findByHabitosAndRango(activos, LUNES, DOMINGO)).thenReturn(List.of(
                hecho(gimnasio, LUNES), hecho(yoga, MARTES), hecho(gimnasio, MIERCOLES),
                hecho(llamar, JUEVES),
                hecho(agua, SABADO), hecho(agua, SABADO), hecho(agua, SABADO), hecho(meditar, SABADO)));
        Map<Integer, RitmoLeido> ritmos = Map.of(1, leido(0.85), 2, leido(0.65), 3, leido(0.50),
                4, leido(0.97), 5, leido(0.70), 6, leido(0.85));
        when(ritmoService.ritmosLeidos(activos, hoy)).thenReturn(ritmos);
        when(ritmoService.generalLeido(activos, ritmos, hoy))
                .thenReturn(Optional.of(new RitmoLeido(0.72, 0.66)));
    }

    private List<String> nombres(SeccionHoyDTO seccion) {
        return seccion.habitos().stream().map(fila -> fila.habito().getNombre()).toList();
    }

    private FilaHoyDTO fila(SeccionHoyDTO seccion, String nombre) {
        return seccion.habitos().stream()
                .filter(fila -> fila.habito().getNombre().equals(nombre))
                .findFirst().orElseThrow();
    }

    private void assertFila(FilaHoyDTO fila, int completados, int meta, boolean hecho,
                            boolean completadoEnFecha, int ritmoNivel) {
        String quien = fila.habito().getNombre();
        assertEquals(completados, fila.completados(), quien + ": completados");
        assertEquals(meta, fila.meta(), quien + ": meta");
        assertEquals(hecho, fila.hecho(), quien + ": hecho");
        assertEquals(completadoEnFecha, fila.completadoEnFecha(), quien + ": completadoEnFecha");
        assertEquals(ritmoNivel, fila.ritmoNivel(), quien + ": ritmoNivel");
    }

    private void assertTira(HoyDTO hoy, Double... avances) {
        assertEquals(7, hoy.dias().size());
        for (int i = 0; i < 7; i++) {
            Double real = hoy.dias().get(i).avance();
            if (avances[i] == null) {
                assertNull(real, "día " + i);
            } else {
                assertNotNull(real, "día " + i);
                assertEquals(avances[i], real, D, "día " + i);
            }
        }
    }

    @Test
    void elEjemploDeLaMaqueta() {
        laSemanaDeLaMaqueta(SABADO);

        HoyDTO hoy = hoyService.obtenerHoy(usuario, SABADO, SABADO);

        assertEquals("2026-10-10", hoy.fecha());
        assertEquals("2026-10-10", hoy.hoy());
        assertEquals(0.4375, hoy.avance(), D);
        assertEquals(3, hoy.ritmoNivel());
        assertEquals(Tendencia.SUBE, hoy.ritmoTendencia());

        // HOY · 1 de 4
        assertEquals(List.of("Beber agua", "Leer 20 minutos", "Meditar", "Yoga"), nombres(hoy.delDia()));
        assertEquals(1, hoy.delDia().hechos());
        assertEquals(4, hoy.delDia().total());
        assertFila(fila(hoy.delDia(), "Beber agua"), 3, 4, false, true, 4);
        assertFila(fila(hoy.delDia(), "Leer 20 minutos"), 0, 1, false, false, 3);
        assertFila(fila(hoy.delDia(), "Meditar"), 1, 1, true, true, 5);
        assertFila(fila(hoy.delDia(), "Yoga"), 1, 3, false, false, 2);
        assertEquals(List.of("2026-10-10", "2026-10-10", "2026-10-10"),
                fila(hoy.delDia(), "Beber agua").fechasCompletadas());

        // ESTA SEMANA · 1 de 2
        assertEquals(List.of("Gimnasio", "Llamar a casa"), nombres(hoy.deLaSemana()));
        assertEquals(1, hoy.deLaSemana().hechos());
        assertEquals(2, hoy.deLaSemana().total());
        assertFila(fila(hoy.deLaSemana(), "Gimnasio"), 2, 5, false, false, 3);
        assertFila(fila(hoy.deLaSemana(), "Llamar a casa"), 1, 1, true, false, 4);

        // La tira: el martes se hizo yoga; el sábado es el de arriba; el
        // domingo aún no ha llegado.
        assertEquals("2026-10-05", hoy.dias().get(0).fecha());
        assertEquals("2026-10-11", hoy.dias().get(6).fecha());
        assertTira(hoy, 0.0, 0.25, 0.0, 0.0, 0.0, 0.4375, null);
    }

    @Test
    void verUnDiaPasado_ensenaLoQueTocabaEseDia_conElRitmoDeHoy() {
        laSemanaDeLaMaqueta(SABADO);

        HoyDTO martes = hoyService.obtenerHoy(usuario, MARTES, SABADO);

        assertEquals("2026-10-06", martes.fecha());
        assertEquals("2026-10-10", martes.hoy());
        assertEquals(0.25, martes.avance(), D);
        assertEquals(List.of("Beber agua", "Leer 20 minutos", "Meditar", "Yoga"), nombres(martes.delDia()));
        assertEquals(1, martes.delDia().hechos());
        assertFila(fila(martes.delDia(), "Beber agua"), 0, 4, false, false, 4);
        assertFila(fila(martes.delDia(), "Yoga"), 1, 3, true, true, 2);
        // La sección de la semana es la misma se mire el día que se mire.
        assertEquals(List.of("Gimnasio", "Llamar a casa"), nombres(martes.deLaSemana()));
        assertEquals(1, martes.deLaSemana().hechos());
        verify(ritmoService).ritmosLeidos(activos, SABADO);
    }

    @Test
    void unSemanalConDiasFijos_fueraDeSuDiaVaEnLaSeccionDeLaSemana() {
        laSemanaDeLaMaqueta(SABADO);

        HoyDTO viernes = hoyService.obtenerHoy(usuario, VIERNES, SABADO);

        assertEquals(List.of("Beber agua", "Leer 20 minutos", "Meditar"), nombres(viernes.delDia()));
        assertEquals(List.of("Gimnasio", "Llamar a casa", "Yoga"), nombres(viernes.deLaSemana()));
        assertFila(fila(viernes.deLaSemana(), "Yoga"), 1, 3, false, false, 2);
        assertEquals(1, viernes.deLaSemana().hechos());
        assertEquals(3, viernes.deLaSemana().total());
    }

    @Test
    void sinHabitos_todoVacio() {
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());

        HoyDTO hoy = hoyService.obtenerHoy(usuario, SABADO, SABADO);

        assertNull(hoy.avance());
        assertNull(hoy.ritmoNivel());
        assertNull(hoy.ritmoTendencia());
        assertEquals(0, hoy.delDia().total());
        assertEquals(0, hoy.deLaSemana().total());
        assertTira(hoy, null, null, null, null, null, null, null);
    }

    @Test
    void sinFecha_usaElHoyDeLaZonaDelUsuario() {
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(MADRID);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());

        HoyDTO hoy = hoyService.obtenerHoy(usuario, null);

        assertEquals(LocalDate.now(MADRID).toString(), hoy.hoy());
        assertEquals(hoy.hoy(), hoy.fecha());
    }

    @Test
    void losRegistrosSinCompletarNoCuentan() {
        List<Habito> soloLeer = List.of(leer);
        Map<Integer, RitmoLeido> ritmos = Map.of(2, leido(0.5));
        when(habitoDAO.findActivos(usuario)).thenReturn(soloLeer);
        when(registroDAO.findByHabitosAndRango(soloLeer, LUNES, DOMINGO))
                .thenReturn(List.of(new Registro(leer, false, "", SABADO)));
        when(ritmoService.ritmosLeidos(soloLeer, SABADO)).thenReturn(ritmos);
        when(ritmoService.generalLeido(soloLeer, ritmos, SABADO)).thenReturn(Optional.of(leido(0.5)));

        HoyDTO hoy = hoyService.obtenerHoy(usuario, SABADO, SABADO);

        assertFila(fila(hoy.delDia(), "Leer 20 minutos"), 0, 1, false, false, 2);
        assertEquals(0.0, hoy.avance(), D);
        assertEquals(2, hoy.ritmoNivel());
        assertEquals(Tendencia.IGUAL, hoy.ritmoTendencia());
    }

    @Test
    void unHabitoCreadoDespuesDelDiaQueSeMira_noSaleEseDia() {
        leer.setFechaInicio(VIERNES);
        List<Habito> soloLeer = List.of(leer);
        Map<Integer, RitmoLeido> ritmos = Map.of(2, leido(0.5));
        when(habitoDAO.findActivos(usuario)).thenReturn(soloLeer);
        when(ritmoService.ritmosLeidos(soloLeer, SABADO)).thenReturn(ritmos);
        when(ritmoService.generalLeido(soloLeer, ritmos, SABADO)).thenReturn(Optional.of(leido(0.5)));

        HoyDTO martes = hoyService.obtenerHoy(usuario, MARTES, SABADO);

        assertEquals(0, martes.delDia().total());
        assertEquals(0, martes.deLaSemana().total());
        assertNull(martes.avance());
        // De lunes a jueves no existía; viernes y sábado, sin hacer; domingo, por llegar.
        assertTira(martes, null, null, null, null, 0.0, 0.0, null);
    }

    @Test
    void alAbrirHoy_seConcedenLosLogrosDeRitmoCumplidos_yViajanEnLaRespuesta() {
        // En la lámina hay hábitos en "Buen ritmo", en "Gran ritmo" y uno en
        // "Imparable" (0,97); el general está en "Buen ritmo" y yoga va por
        // debajo, así que se cumplen justo esos tres.
        laSemanaDeLaMaqueta(SABADO);
        when(logrosHabitosService.evaluarLogrosDeRitmo(usuario,
                List.of("RITMO_BUENO", "RITMO_FUERTE", "RITMO_IMPARABLE")))
                .thenReturn(List.of("RITMO_IMPARABLE"));

        HoyDTO hoy = hoyService.obtenerHoy(usuario, SABADO, SABADO);

        assertEquals(List.of("RITMO_IMPARABLE"), hoy.logrosOtorgados());
    }

    @Test
    void sinNingunLogroDeRitmoCumplido_noSePreguntaAlServicioDeLogros() {
        List<Habito> soloLeer = List.of(leer);
        Map<Integer, RitmoLeido> ritmos = Map.of(2, leido(0.5));
        when(habitoDAO.findActivos(usuario)).thenReturn(soloLeer);
        when(ritmoService.ritmosLeidos(soloLeer, SABADO)).thenReturn(ritmos);
        when(ritmoService.generalLeido(soloLeer, ritmos, SABADO)).thenReturn(Optional.of(leido(0.5)));

        HoyDTO hoy = hoyService.obtenerHoy(usuario, SABADO, SABADO);

        assertEquals(List.of(), hoy.logrosOtorgados());
        verify(logrosHabitosService, never()).evaluarLogrosDeRitmo(any(), any());
    }
}
