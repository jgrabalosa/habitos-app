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
    void seAceptanLosTresIdiomasSoportados() {
        assertTrue(PreferenciasService.idiomaValido("es"));
        assertTrue(PreferenciasService.idiomaValido("en"));
        assertTrue(PreferenciasService.idiomaValido("pt"));
    }

    @Test
    void seRechazaUnIdiomaNoSoportado() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        assertThrows(IllegalArgumentException.class,
                () -> preferenciasService.actualizar(1, "fr", null));

        verify(usuarioDAO, never()).update(any());
    }

    @Test
    void seRechazaUnaZonaInexistente() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        assertThrows(IllegalArgumentException.class,
                () -> preferenciasService.actualizar(1, null, "Marte/Olympus"));

        verify(usuarioDAO, never()).update(any());
    }

    @Test
    void idiomaYZonaSonIndependientes_seCambiaSoloElEnviado() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        // Solo idioma: la zona no se toca
        preferenciasService.actualizar(1, "pt", null);
        assertEquals("pt", usuario.getIdioma());
        assertEquals("Europe/Madrid", usuario.getZonaHoraria());

        // Solo zona: el idioma se mantiene en pt. Un brasileño habla portugués
        // pero está cuatro horas por detrás de Portugal.
        preferenciasService.actualizar(1, null, "America/Sao_Paulo");
        assertEquals("pt", usuario.getIdioma());
        assertEquals("America/Sao_Paulo", usuario.getZonaHoraria());
    }

    @Test
    void siElUsuarioNoExiste_seLanzaExcepcionYNoSeGuardaNada() {
        when(usuarioDAO.findById(99)).thenReturn(null);

        assertThrows(RuntimeException.class,
                () -> preferenciasService.actualizar(99, "en", null));

        verify(usuarioDAO, never()).update(any());
    }
}
