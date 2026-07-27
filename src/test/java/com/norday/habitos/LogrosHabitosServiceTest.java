package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.service.LogroService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.LogrosHabitosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.ZoneId;
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
}
