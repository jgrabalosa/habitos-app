package com.joaquim.habitosapp;

import com.joaquim.habitosapp.model.*;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import com.joaquim.habitosapp.service.LogroService;
import com.joaquim.habitosapp.service.MotorLogrosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class MotorLogrosServiceTest {

    @Mock
    private LogroService logroService;

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @InjectMocks
    private MotorLogrosService motorLogrosService;

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

    private Logro crearLogro(int id, String codigo) {
        Logro logro = new Logro();
        logro.setLogroId(id);
        logro.setCodigo(codigo);
        return logro;
    }

    @Test
    void alAlcanzarRacha3PorPrimeraVez_seOtorgaRACHA_3() {
        Racha racha = new Racha(habito);
        racha.setRachaActual(3);
        racha.setRachaMaxima(3); // primera vez que llega, nunca bajó de ahí

        Logro logroRacha3 = crearLogro(1, "RACHA_3");

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.buscarPorCodigo("RACHA_3")).thenReturn(logroRacha3);
        when(logroService.otorgarLogro(usuario, 1)).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertTrue(otorgados.contains("RACHA_3"));
    }

    @Test
    void alRomperYVolverAAlcanzarRacha3_seOtorgaRACHA_RECUPERADA() {
        Racha racha = new Racha(habito);
        racha.setRachaActual(3);
        racha.setRachaMaxima(10); // ya tuvo una racha más alta antes, esta vez volvió a 3

        Logro logroRecuperada = crearLogro(2, "RACHA_RECUPERADA");

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.buscarPorCodigo("RACHA_RECUPERADA")).thenReturn(logroRecuperada);
        when(logroService.otorgarLogro(usuario, 2)).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(20);

        List<String> otorgados = motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertTrue(otorgados.contains("RACHA_RECUPERADA"));
        assertFalse(otorgados.contains("RACHA_3")); // no debe darse el de "primera vez"
    }

    @Test
    void alAlcanzarRacha3SinHaberRotoAntes_noSeOtorgaRACHA_RECUPERADA() {
        Racha racha = new Racha(habito);
        racha.setRachaActual(3);
        racha.setRachaMaxima(3); // nunca bajó, es su récord actual

        Logro logroRacha3 = crearLogro(1, "RACHA_3");

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.buscarPorCodigo("RACHA_3")).thenReturn(logroRacha3);
        when(logroService.otorgarLogro(usuario, 1)).thenReturn(true);
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertFalse(otorgados.contains("RACHA_RECUPERADA"));
    }

    @Test
    void alOtorgarUnLogroYaConseguido_noSeDuplica() {
        Racha racha = new Racha(habito);
        racha.setRachaActual(3);
        racha.setRachaMaxima(3);

        Logro logroRacha3 = crearLogro(1, "RACHA_3");

        when(rachaDAO.findByHabito(habito)).thenReturn(racha);
        when(logroService.buscarPorCodigo("RACHA_3")).thenReturn(logroRacha3);
        when(logroService.otorgarLogro(usuario, 1)).thenReturn(false); // ya lo tenía, LogroService lo rechaza
        when(registroDAO.contarPorUsuario(1)).thenReturn(3);

        List<String> otorgados = motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);

        assertFalse(otorgados.contains("RACHA_3")); // no se añade a la lista si ya lo tenía
    }

    @Test
    void alCrearElTercerHabitoActivo_seOtorgaHABITOS_ACTIVOS_3() {
        List<Habito> tresHabitos = List.of(
                crearHabitoSimple(1), crearHabitoSimple(2), crearHabitoSimple(3)
        );

        Logro logroHabitos3 = crearLogro(5, "HABITOS_ACTIVOS_3");

        when(habitoDAO.findActivos(usuario)).thenReturn(tresHabitos);
        when(logroService.buscarPorCodigo("HABITOS_ACTIVOS_3")).thenReturn(logroHabitos3);
        when(logroService.otorgarLogro(usuario, 5)).thenReturn(true);

        motorLogrosService.evaluarTrasCrearHabito(usuario);

        verify(logroService).otorgarLogro(usuario, 5);
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