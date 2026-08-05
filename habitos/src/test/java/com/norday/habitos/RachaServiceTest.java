package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.service.RachaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Rotura perezosa: la racha muere sola al saltarse un periodo, sin cron. */
@ExtendWith(MockitoExtension.class)
class RachaServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");

    @Mock
    private IRachaDAO rachaDAO;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private RachaService rachaService;

    private Usuario usuario;
    private Habito habito;
    private Racha racha;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setZonaHoraria("Europe/Madrid");

        habito = new Habito();
        habito.setHabitoId(10);
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setMeta(1);
        habito.setPropietario(usuario);

        racha = new Racha(habito, LocalDate.now(MADRID));
        lenient().when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(MADRID);
    }

    @Test
    void cumplidaHoy_laRachaSigueVivaYNoSeToca() {
        racha.setRachaActual(5);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().rangoPeriodoActual(MADRID)[0]);

        assertEquals(5, rachaService.rachaActualVigente(racha));
        verify(rachaDAO, never()).update(any());
    }

    @Test
    void cumplidaAyer_laRachaSigueVivaAunqueHoyNoSeHayaHecho() {
        racha.setRachaActual(5);
        racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().inicioPeriodoAnterior(MADRID));

        assertEquals(5, rachaService.rachaActualVigente(racha));
        verify(rachaDAO, never()).update(any());
    }

    @Test
    void saltadoUnPeriodoEntero_laRachaMuereYSeNormalizaEnBD() {
        racha.setRachaActual(5);
        racha.setRachaMaxima(9);
        // Último cumplimiento hace tres días: se saltó al menos un periodo entero
        racha.setPeriodoMetaAlcanzada(LocalDate.now(MADRID).minusDays(3));

        assertEquals(0, rachaService.rachaActualVigente(racha));
        assertEquals(0, racha.getRachaActual());
        // Se persiste para que ninguna lectura que no pase por aquí mienta
        verify(rachaDAO).update(racha);
        // La mejor racha histórica no se toca: ya era correcta
        assertEquals(9, racha.getRachaMaxima());
    }

    @Test
    void sinSelloDePeriodo_laRachaSeConsideraMuerta() {
        racha.setRachaActual(4);
        racha.setPeriodoMetaAlcanzada(null);

        assertEquals(0, rachaService.rachaActualVigente(racha));
        verify(rachaDAO).update(racha);
    }

    @Test
    void rachaYaACero_noSeEscribeNada() {
        racha.setRachaActual(0);

        assertEquals(0, rachaService.rachaActualVigente(racha));
        verify(rachaDAO, never()).update(any());
    }

    @Test
    void rachaNula_devuelveCeroSinLanzar() {
        assertEquals(0, rachaService.rachaActualVigente((Racha) null));
    }

    @Test
    void elSelloSeAutocaduca_mismoDatoVivoEnUnaZonaYMuertoEnOtra() {
        // Sello puesto "ayer en Madrid". Para un usuario cuyo hoy va por
        // delante, ese mismo sello puede quedar fuera de la ventana viva.
        LocalDate ayerEnMadrid = habito.getFrecuencia().inicioPeriodoAnterior(MADRID);
        racha.setPeriodoMetaAlcanzada(ayerEnMadrid);

        assertTrue(racha.sigueViva(MADRID));
        // Nadie ha tenido que limpiar ningún flag: la comparación se hace sola
        assertFalse(racha.metaAlcanzadaEnPeriodoActual(MADRID));
    }
}
