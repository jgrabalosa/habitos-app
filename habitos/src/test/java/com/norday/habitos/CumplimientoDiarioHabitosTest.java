package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.CumplimientoDiarioHabitos;
import com.norday.habitos.service.HabitoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Lo que hábitos le contesta al motor sobre cómo va hoy el usuario. La regla
 * tiene sus tests en ComoVaHoyTest; aquí se comprueba que se le dan bien los
 * datos: los registros desde el lunes de la semana pasada, cuáles son de
 * esta semana y la fecha de llegada como referencia.
 *
 * "Hoy" es el sábado 10-oct-2026; el lunes de la semana pasada, el 28-sep.
 */
@ExtendWith(MockitoExtension.class)
class CumplimientoDiarioHabitosTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate LUNES_DE_LA_SEMANA_PASADA = LocalDate.of(2026, 9, 28);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    @Mock
    private HabitoService habitoService;

    @Mock
    private IUsuarioDAO usuarioDAO;

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private CumplimientoDiarioHabitos cumplimiento;

    private Usuario usuario;
    private Habito leer;
    private Habito gimnasio;

    private Habito habito(int id, Frecuencia frecuencia, int meta) {
        Habito habito = new Habito();
        habito.setHabitoId(id);
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setPropietario(usuario);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
        return habito;
    }

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setFechaRegistro(LocalDateTime.of(2026, 1, 1, 10, 0));
        leer = habito(1, Frecuencia.DIARIO, 1);
        gimnasio = habito(2, Frecuencia.SEMANAL, 5);
    }

    private void conRegistros(List<Habito> activos, Registro... registros) {
        when(habitoDAO.findActivos(usuario)).thenReturn(activos);
        when(registroDAO.findByHabitosAndRango(activos, LUNES_DE_LA_SEMANA_PASADA, HOY))
                .thenReturn(List.of(registros));
    }

    @Test
    void conLoDeHoyHecho_esCompleto() {
        conRegistros(List.of(leer), new Registro(leer, true, "", HOY));

        assertEquals(AvanceDeHoy.COMPLETO, cumplimiento.comoVaHoy(usuario, HOY, MADRID));
    }

    @Test
    void conLoUltimoHaceTresDiasEnteros_estaAusente() {
        // Hecho el martes 6: miércoles, jueves y viernes sin nada.
        conRegistros(List.of(leer), new Registro(leer, true, "", LocalDate.of(2026, 10, 6)));

        assertEquals(AvanceDeHoy.AUSENTE, cumplimiento.comoVaHoy(usuario, HOY, MADRID));
    }

    @Test
    void conLoUltimoAyer_estaSinEmpezar() {
        conRegistros(List.of(leer), new Registro(leer, true, "", LocalDate.of(2026, 10, 9)));

        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(usuario, HOY, MADRID));
    }

    @Test
    void unaSesionDeLaSemanaPasada_noEmpiezaEstaSemana_peroCuentaComoActividad() {
        // Solo un semanal, con una sesión el miércoles 30-sep. Si contase
        // como de esta semana sería EMPEZADO; si no contase como actividad,
        // AUSENTE.
        conRegistros(List.of(gimnasio), new Registro(gimnasio, true, "", LocalDate.of(2026, 9, 30)));

        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(usuario, HOY, MADRID));
    }

    @Test
    void sinRegistros_laReferenciaEsElDiaEnQueLlego() {
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of(leer));

        usuario.setFechaRegistro(LocalDateTime.of(2026, 10, 9, 10, 0));
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(usuario, HOY, MADRID));

        usuario.setFechaRegistro(LocalDateTime.of(2026, 9, 10, 10, 0));
        assertEquals(AvanceDeHoy.AUSENTE, cumplimiento.comoVaHoy(usuario, HOY, MADRID));
    }

    @Test
    void losRegistrosSinCompletarNoCuentan() {
        usuario.setFechaRegistro(LocalDateTime.of(2026, 10, 9, 10, 0));
        conRegistros(List.of(leer), new Registro(leer, false, "", HOY));

        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(usuario, HOY, MADRID));
    }

    @Test
    void sinUsuario_noSeLeDaPorAusente() {
        when(usuarioDAO.findById(9)).thenReturn(null);

        assertEquals(AvanceDeHoy.SIN_EMPEZAR, cumplimiento.comoVaHoy(9));
    }

    @Test
    void elMotorPreguntaPorId_ySeUsaElHoyDeLaZonaDelUsuario() {
        // Llegó en enero y no tiene hábitos ni registros: ausente, sea cual
        // sea el día en que se ejecute este test.
        when(usuarioDAO.findById(1)).thenReturn(usuario);
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(MADRID);
        when(habitoDAO.findActivos(usuario)).thenReturn(List.of());

        assertEquals(AvanceDeHoy.AUSENTE, cumplimiento.comoVaHoy(1));
    }
}
