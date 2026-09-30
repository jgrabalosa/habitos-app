package com.norday.core;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.OrigenInstalacionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDateTime;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class OrigenInstalacionServiceTest {

    @Mock
    private IUsuarioDAO usuarioDAO;

    @InjectMocks
    private OrigenInstalacionService servicio;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
    }

    @Test
    void extraerParametros_referrerNormal_devuelveLosTres() {
        Map<String, String> p = OrigenInstalacionService.extraerParametros(
                "utm_source=instagram&utm_medium=social&utm_campaign=pildora");

        assertEquals(3, p.size());
        assertEquals("instagram", p.get("utm_source"));
        assertEquals("social", p.get("utm_medium"));
        assertEquals("pildora", p.get("utm_campaign"));
    }

    @Test
    void extraerParametros_valoresCodificados_losDecodifica() {
        Map<String, String> p = OrigenInstalacionService.extraerParametros(
                "utm_campaign=p%C3%ADldora%20hoy");

        assertEquals("píldora hoy", p.get("utm_campaign"));
    }

    @Test
    void extraerParametros_ignoraParesSinIgualOValorVacio_yGanaLaPrimeraClaveRepetida() {
        Map<String, String> p = OrigenInstalacionService.extraerParametros(
                "sinigual&utm_medium=&utm_source=primero&utm_source=segundo&=suelto");

        assertEquals(1, p.size());
        assertEquals("primero", p.get("utm_source"));
    }

    @Test
    void extraerParametros_nuloYVacio_devuelveMapaVacio() {
        assertTrue(OrigenInstalacionService.extraerParametros(null).isEmpty());
        assertTrue(OrigenInstalacionService.extraerParametros("").isEmpty());
    }

    @Test
    void registrar_usuarioSinOrigen_guardaTodoYDevuelveTrue() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);
        String referrer = "utm_source=instagram&utm_medium=social&utm_campaign=pildora";

        boolean guardado = servicio.registrar(1, referrer);

        assertTrue(guardado);
        assertEquals(referrer, usuario.getReferrerCrudo());
        assertEquals("instagram", usuario.getUtmSource());
        assertEquals("social", usuario.getUtmMedium());
        assertEquals("pildora", usuario.getUtmCampaign());
        assertNotNull(usuario.getReferrerFecha());
        verify(usuarioDAO, times(1)).update(usuario);
    }

    @Test
    void registrar_recortaReferrerYUtmSource() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);
        String fuenteLarga = "a".repeat(150);
        String referrer = "utm_source=" + fuenteLarga + "&x=" + "b".repeat(600);

        servicio.registrar(1, referrer);

        assertEquals(500, usuario.getReferrerCrudo().length());
        assertEquals(100, usuario.getUtmSource().length());
    }

    @Test
    void registrar_referrerVacio_guardaCrudoVacioUtmNulosYFecha() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        boolean guardado = servicio.registrar(1, "");

        assertTrue(guardado);
        assertEquals("", usuario.getReferrerCrudo());
        assertNull(usuario.getUtmSource());
        assertNull(usuario.getUtmMedium());
        assertNull(usuario.getUtmCampaign());
        assertNotNull(usuario.getReferrerFecha());
        verify(usuarioDAO, times(1)).update(usuario);
    }

    @Test
    void registrar_usuarioQueYaTieneOrigen_devuelveFalseYNoCambiaNada() {
        LocalDateTime fechaPrevia = LocalDateTime.of(2026, 9, 1, 10, 0);
        usuario.setReferrerFecha(fechaPrevia);
        usuario.setReferrerCrudo("utm_source=tiktok");
        usuario.setUtmSource("tiktok");
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        boolean guardado = servicio.registrar(1, "utm_source=instagram&utm_medium=social");

        assertFalse(guardado);
        verify(usuarioDAO, never()).update(any());
        assertEquals(fechaPrevia, usuario.getReferrerFecha());
        assertEquals("utm_source=tiktok", usuario.getReferrerCrudo());
        assertEquals("tiktok", usuario.getUtmSource());
        assertNull(usuario.getUtmMedium());
        assertNull(usuario.getUtmCampaign());
    }

    @Test
    void registrar_usuarioInexistente_lanzaRecursoNoEncontrado() {
        when(usuarioDAO.findById(99)).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class,
                () -> servicio.registrar(99, "utm_source=instagram"));
        verify(usuarioDAO, never()).update(any());
    }
}
