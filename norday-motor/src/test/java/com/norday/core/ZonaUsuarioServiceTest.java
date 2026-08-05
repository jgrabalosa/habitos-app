package com.norday.core;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;

class ZonaUsuarioServiceTest {

    private ZonaUsuarioService zonaUsuarioService;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        zonaUsuarioService = new ZonaUsuarioService();
        usuario = new Usuario();
        usuario.setUsuarioId(1);
    }

    @Test
    void zonaValida_seDevuelveTalCual() {
        usuario.setZonaHoraria("America/Sao_Paulo");
        assertEquals(ZoneId.of("America/Sao_Paulo"), zonaUsuarioService.zonaDe(usuario));
    }

    @Test
    void zonaNula_caeALaPorDefecto() {
        usuario.setZonaHoraria(null);
        assertEquals(ZoneId.of(Usuario.ZONA_POR_DEFECTO), zonaUsuarioService.zonaDe(usuario));
    }

    @Test
    void zonaVaciaOEnBlanco_caeALaPorDefecto() {
        usuario.setZonaHoraria("   ");
        assertEquals(ZoneId.of(Usuario.ZONA_POR_DEFECTO), zonaUsuarioService.zonaDe(usuario));
    }

    @Test
    void zonaBasura_caeALaPorDefectoSinLanzar() {
        usuario.setZonaHoraria("Marte/Olympus_Mons");
        assertDoesNotThrow(() -> zonaUsuarioService.zonaDe(usuario));
        assertEquals(ZoneId.of(Usuario.ZONA_POR_DEFECTO), zonaUsuarioService.zonaDe(usuario));
    }

    @Test
    void usuarioNulo_caeALaPorDefectoSinLanzar() {
        assertDoesNotThrow(() -> zonaUsuarioService.zonaDe((Usuario) null));
        assertEquals(ZoneId.of(Usuario.ZONA_POR_DEFECTO), zonaUsuarioService.zonaDe((Usuario) null));
    }

    @Test
    void dosUsuariosEnZonasOpuestas_puedenEstarEnDiasDistintos() {
        Usuario enMadrid = new Usuario();
        enMadrid.setZonaHoraria("Europe/Madrid");
        Usuario enAuckland = new Usuario();
        enAuckland.setZonaHoraria("Pacific/Auckland");

        // No forzamos qué día es cada uno (depende de la hora real de
        // ejecución), pero sí que cada uno resuelve su propia zona.
        assertNotEquals(zonaUsuarioService.zonaDe(enMadrid), zonaUsuarioService.zonaDe(enAuckland));
        assertNotNull(zonaUsuarioService.hoyDe(enMadrid));
        assertNotNull(zonaUsuarioService.hoyDe(enAuckland));
    }
}
