package com.norday.core;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.PreferenciasService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenciasServiceTest {

    @Mock
    private IUsuarioDAO usuarioDAO;

    @InjectMocks
    private PreferenciasService preferenciasService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
    }

    @Test
    void porDefecto_elUsuarioNaceEnEspanolYMadrid() {
        assertEquals("es", usuario.getIdioma());
        assertEquals("Europe/Madrid", usuario.getZonaHoraria());
    }

    @Test
    void porDefecto_losAvisosDeLaAppEstanEncendidos() {
        assertTrue(usuario.isAvisosApp());
    }

    @Test
    void seAceptanLosTresIdiomasSoportados() {
        assertTrue(PreferenciasService.idiomaValido("es"));
        assertTrue(PreferenciasService.idiomaValido("en"));
        assertTrue(PreferenciasService.idiomaValido("pt"));
    }

    @Test
    void seRechazaUnIdiomaNoSoportado() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        assertThrows(IllegalArgumentException.class,
                () -> preferenciasService.actualizar(1, "fr", null, null));

        verify(usuarioDAO, never()).update(any());
    }

    @Test
    void seRechazaUnaZonaInexistente() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        assertThrows(IllegalArgumentException.class,
                () -> preferenciasService.actualizar(1, null, "Marte/Olympus", null));

        verify(usuarioDAO, never()).update(any());
    }

    @Test
    void idiomaYZonaSonIndependientes_seCambiaSoloElEnviado() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        // Solo idioma: la zona no se toca
        preferenciasService.actualizar(1, "pt", null, null);
        assertEquals("pt", usuario.getIdioma());
        assertEquals("Europe/Madrid", usuario.getZonaHoraria());

        // Solo zona: el idioma se mantiene en pt. Un brasileño habla portugués
        // pero está cuatro horas por detrás de Portugal.
        preferenciasService.actualizar(1, null, "America/Sao_Paulo", null);
        assertEquals("pt", usuario.getIdioma());
        assertEquals("America/Sao_Paulo", usuario.getZonaHoraria());
    }

    @Test
    void avisosApp_seApagaYSeEnciende_sinTocarIdiomaNiZona() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        preferenciasService.actualizar(1, null, null, false);
        assertFalse(usuario.isAvisosApp());
        assertEquals("es", usuario.getIdioma());
        assertEquals("Europe/Madrid", usuario.getZonaHoraria());

        preferenciasService.actualizar(1, null, null, true);
        assertTrue(usuario.isAvisosApp());
        verify(usuarioDAO, times(2)).update(usuario);
    }

    @Test
    void avisosApp_nullEsSinCambio_tambienSiSeCambiaOtraPreferencia() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);
        usuario.setAvisosApp(false);

        // Lo que manda la app instalada: solo textos, sin avisosApp.
        preferenciasService.actualizar(1, "en", "Europe/London", null);

        assertFalse(usuario.isAvisosApp(), "un campo ausente no vuelve a encender los avisos");
        assertEquals("en", usuario.getIdioma());
    }

    @Test
    void booleano_aceptaBooleanosYLosTextosTrueFalse() {
        assertEquals(Boolean.TRUE, PreferenciasService.booleano(true, "avisosApp"));
        assertEquals(Boolean.FALSE, PreferenciasService.booleano(false, "avisosApp"));
        assertEquals(Boolean.TRUE, PreferenciasService.booleano("true", "avisosApp"));
        assertEquals(Boolean.FALSE, PreferenciasService.booleano("FALSE", "avisosApp"));
        assertNull(PreferenciasService.booleano(null, "avisosApp"));
    }

    @Test
    void booleano_rechazaCualquierOtroValor() {
        assertThrows(IllegalArgumentException.class,
                () -> PreferenciasService.booleano("quizá", "avisosApp"));
        assertThrows(IllegalArgumentException.class,
                () -> PreferenciasService.booleano(0, "avisosApp"));
    }

    @Test
    void texto_aceptaTextoYNull_ynoUnNumero() {
        assertEquals("es", PreferenciasService.texto("es", "idioma"));
        assertNull(PreferenciasService.texto(null, "idioma"));
        assertThrows(IllegalArgumentException.class,
                () -> PreferenciasService.texto(5, "idioma"));
    }

    @Test
    void siElUsuarioNoExiste_seLanzaExcepcionYNoSeGuardaNada() {
        when(usuarioDAO.findById(99)).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> preferenciasService.actualizar(99, "en", null, null));

        verify(usuarioDAO, never()).update(any());
    }
}
