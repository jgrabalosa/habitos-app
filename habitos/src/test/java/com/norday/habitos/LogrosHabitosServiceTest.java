package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.service.LogroService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.LogrosHabitosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class LogrosHabitosServiceTest {

    private static final LocalDate HOY = LocalDate.now(ZoneId.of("Europe/Madrid"));

    @Mock
    private LogroService logroService;

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @InjectMocks
    private LogrosHabitosService logrosHabitosService;

    private Usuario usuario;
    private Habito habito;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);

        habito = new Habito();
        habito.setHabitoId(10);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);
    }

    @Disabled("B3: los logros de constancia van por días hechos, no por racha. Los sustituyen los tests de días hechos de esta clase")
    @Test
    void alAlcanzarRacha3PorPrimeraVez_seOtorgaRACHA_3() {
        Racha racha = new Racha(habito, HOY);
        racha.setRachaActual(3);
        racha.setRachaMaxima(3); // primera vez que llega, nunca bajó de ahí

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_3")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertTrue(otorgados.contains("RACHA_3"));
    }

    @Disabled("B3: la regla de Resiliencia por racha deja de aplicarse. Su regla nueva llega en su propio paso")
    @Test
    void alRomperYVolverAAlcanzarRacha3_seOtorgaRACHA_RECUPERADA() {
        Racha racha = new Racha(habito, HOY);
        racha.setRachaActual(3);
        racha.setRachaMaxima(10); // ya tuvo una racha más alta antes, esta vez volvió a 3

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_RECUPERADA")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(20);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertTrue(otorgados.contains("RACHA_RECUPERADA"));
        assertFalse(otorgados.contains("RACHA_3")); // no debe darse el de "primera vez"
    }

    @Disabled("B3: la regla de Resiliencia por racha deja de aplicarse. Su regla nueva llega en su propio paso")
    @Test
    void alAlcanzarRacha3SinHaberRotoAntes_noSeOtorgaRACHA_RECUPERADA() {
        Racha racha = new Racha(habito, HOY);
        racha.setRachaActual(3);
        racha.setRachaMaxima(3); // nunca bajó, es su récord actual

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_3")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertFalse(otorgados.contains("RACHA_RECUPERADA"));
    }

    @Disabled("B3: los logros de constancia van por días hechos, no por racha. Los sustituyen los tests de días hechos de esta clase")
    @Test
    void alOtorgarUnLogroYaConseguido_noSeDuplica() {
        Racha racha = new Racha(habito, HOY);
        racha.setRachaActual(3);
        racha.setRachaMaxima(3);

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        // ya lo tenía: LogroService lo rechaza y no se añade a la lista devuelta
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_3")).thenReturn(false);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertFalse(otorgados.contains("RACHA_3")); // no se añade a la lista si ya lo tenía
    }

    @Disabled("B3: los logros de constancia van por días hechos, no por racha. Los sustituyen los tests de días hechos de esta clase")
    @Test
    void alAlcanzarUnUmbralIntermedio_seOtorgaElLogroCorrespondiente() {
        Racha racha = new Racha(habito, HOY);
        racha.setRachaActual(45);
        racha.setRachaMaxima(45);

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_45")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(45);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertTrue(otorgados.contains("RACHA_45"));
    }

    @Disabled("B3: los logros de constancia van por días hechos, no por racha. Los sustituyen los tests de días hechos de esta clase")
    @Test
    void alQuedarEntreDosUmbrales_noSeOtorgaNingunLogroDeRacha() {
        Racha racha = new Racha(habito, HOY);
        racha.setRachaActual(46);
        racha.setRachaMaxima(46);

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(registroDAO.contarPorUsuario(1)).thenReturn(46);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertFalse(otorgados.stream().anyMatch(c -> c.startsWith("RACHA_")));
    }

    @Test
    void alCrearElTercerHabitoActivo_seOtorgaHABITOS_ACTIVOS_3() {
        List<Habito> tresHabitos = List.of(
                crearHabitoSimple(1), crearHabitoSimple(2), crearHabitoSimple(3)
        );

        when(habitoDAO.findActivos(usuario)).thenReturn(tresHabitos);
        when(logroService.otorgarSiNoTiene(usuario, "HABITOS_ACTIVOS_3")).thenReturn(true);

        logrosHabitosService.evaluarTrasCrearHabito(usuario);

        verify(logroService).otorgarSiNoTiene(usuario, "HABITOS_ACTIVOS_3");
    }

    private Habito crearHabitoSimple(int id) {
        Habito h = new Habito();
        h.setHabitoId(id);
        h.setFrecuencia(Frecuencia.DIARIO);
        h.setMeta(1);
        h.setPropietario(usuario);
        return h;
    }

    /** Un registro completado por cada número: los días que hace, contados desde hoy. */
    private List<Registro> hechosHace(int... dias) {
        List<Registro> registros = new ArrayList<>();
        for (int dia : dias) {
            registros.add(new Registro(habito, true, "", HOY.minusDays(dia)));
        }
        return registros;
    }

    @Test
    void conTresDiasHechos_seOtorgaElLogroDeTresAunqueNoSeanSeguidos() {
        when(registroDAO.findByHabito(habito)).thenReturn(hechosHace(20, 5, 0));
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_3")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertEquals(List.of("RACHA_3"), otorgados);
    }

    @Test
    void conDoceDiasHechosYNingunLogroGanado_seOtorganLosTresEscalonesSuperados() {
        when(registroDAO.findByHabito(habito))
                .thenReturn(hechosHace(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        when(logroService.otorgarSiNoTiene(eq(usuario), anyString())).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(12);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertEquals(List.of("RACHA_3", "RACHA_7", "RACHA_10"), otorgados);
        verify(logroService, never()).otorgarSiNoTiene(usuario, "RACHA_15");
    }

    @Test
    void losLogrosQueYaTiene_noSeRepiten() {
        when(registroDAO.findByHabito(habito))
                .thenReturn(hechosHace(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12));
        // Los de 3 y 7 ya los tenía: el motor los rechaza.
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_3")).thenReturn(false);
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_7")).thenReturn(false);
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_10")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(12);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertEquals(List.of("RACHA_10"), otorgados);
    }

    @Test
    void enUnDiario_soloCuentanLosDiasQueLlegaronAlObjetivo() {
        // Diario de 2: dos días completos y uno a medias son 2 días hechos.
        habito.setMeta(2);
        when(registroDAO.findByHabito(habito)).thenReturn(hechosHace(3, 3, 2, 2, 1));
        when(registroDAO.contarPorUsuario(1)).thenReturn(5);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertTrue(otorgados.isEmpty());
        verify(logroService, never()).otorgarSiNoTiene(any(), anyString());
    }

    @Test
    void enUnSemanal_cadaSesionCuentaComoDiaHecho() {
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(5);
        when(registroDAO.findByHabito(habito)).thenReturn(hechosHace(15, 8, 1));
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_3")).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertEquals(List.of("RACHA_3"), otorgados);
    }

    @Test
    void alVolverAUnHabito_seOtorgaResiliencia() {
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_RECUPERADA")).thenReturn(true);

        assertEquals(List.of("RACHA_RECUPERADA"), logrosHabitosService.evaluarTrasVolver(usuario));
    }

    @Test
    void alVolverConResilienciaYaGanada_noSeRepite() {
        when(logroService.otorgarSiNoTiene(usuario, "RACHA_RECUPERADA")).thenReturn(false);

        assertTrue(logrosHabitosService.evaluarTrasVolver(usuario).isEmpty());
    }

    @Test
    void deLosLogrosDeRitmoCumplidos_seConcedenSoloLosQueNoTenia() {
        when(logroService.otorgarSiNoTiene(usuario, "RITMO_BUENO")).thenReturn(false);
        when(logroService.otorgarSiNoTiene(usuario, "RITMO_FUERTE")).thenReturn(true);

        List<String> otorgados = logrosHabitosService.evaluarLogrosDeRitmo(
                usuario, List.of("RITMO_BUENO", "RITMO_FUERTE"));

        assertEquals(List.of("RITMO_FUERTE"), otorgados);
    }
}
