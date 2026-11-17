package com.norday.gamificacion;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LogroServiceTest {

    @Mock
    private ILogroDAO logroDAO;

    @Mock
    private IUsuarioLogroDAO usuarioLogroDAO;

    @Mock
    private UsuarioMonedaService usuarioMonedaService;

    @InjectMocks
    private LogroService logroService;

    private Usuario usuario;
    private Logro logro;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);

        logro = new Logro("LOGRO_PRUEBA", "Logro de prueba", "Descripción", "General", "BRONCE", 50, "icono");
        logro.setLogroId(7);
    }

    @Test
    void catalogoActivo_sinApp_devuelveElCatalogoEntero() {
        List<Logro> activos = List.of(logro);
        when(logroDAO.findActivos()).thenReturn(activos);

        assertSame(activos, logroService.catalogoActivo(null));
        verify(logroDAO, never()).findActivosParaApp(any());
    }

    @Test
    void catalogoActivo_appEnBlanco_devuelveElCatalogoEntero() {
        List<Logro> activos = List.of(logro);
        when(logroDAO.findActivos()).thenReturn(activos);

        assertSame(activos, logroService.catalogoActivo("  "));
        verify(logroDAO, never()).findActivosParaApp(any());
    }

    @Test
    void catalogoActivo_conApp_devuelveElCatalogoDeEsaApp() {
        List<Logro> deLaApp = List.of(logro);
        when(logroDAO.findActivosParaApp("app")).thenReturn(deLaApp);

        assertSame(deLaApp, logroService.catalogoActivo("app"));
        verify(logroDAO, never()).findActivos();
    }

    @Test
    void otorgarSiNoTiene_codigoInexistente_devuelveFalseSinEfectos() {
        when(logroDAO.findByCodigo("NO_EXISTE")).thenReturn(null);

        assertFalse(logroService.otorgarSiNoTiene(usuario, "NO_EXISTE"));

        verify(usuarioLogroDAO, never()).save(any());
        verifyNoInteractions(usuarioMonedaService);
    }

    @Test
    void otorgarLogro_yaLoTiene_devuelveFalseSinBuscarNiGuardar() {
        when(usuarioLogroDAO.existePorUsuarioYLogro(1, 7)).thenReturn(true);

        assertFalse(logroService.otorgarLogro(usuario, 7));

        verify(logroDAO, never()).findById(anyInt());
        verify(usuarioLogroDAO, never()).save(any());
        verifyNoInteractions(usuarioMonedaService);
    }

    @Test
    void otorgarLogro_idInexistente_devuelveFalseSinEfectos() {
        when(usuarioLogroDAO.existePorUsuarioYLogro(1, 99)).thenReturn(false);
        when(logroDAO.findById(99)).thenReturn(null);

        assertFalse(logroService.otorgarLogro(usuario, 99));

        verify(usuarioLogroDAO, never()).save(any());
        verifyNoInteractions(usuarioMonedaService);
    }

    @Test
    void otorgarLogro_logroInactivo_devuelveFalseSinEfectos() {
        logro.setActivo(false);
        when(usuarioLogroDAO.existePorUsuarioYLogro(1, 7)).thenReturn(false);
        when(logroDAO.findById(7)).thenReturn(logro);

        assertFalse(logroService.otorgarLogro(usuario, 7));

        verify(usuarioLogroDAO, never()).save(any());
        verifyNoInteractions(usuarioMonedaService);
    }

    @Test
    void otorgarLogro_correcto_guardaElLogroYRegistraLosPuntos() {
        when(usuarioLogroDAO.existePorUsuarioYLogro(1, 7)).thenReturn(false);
        when(logroDAO.findById(7)).thenReturn(logro);

        assertTrue(logroService.otorgarLogro(usuario, 7));

        ArgumentCaptor<UsuarioLogro> guardado = ArgumentCaptor.forClass(UsuarioLogro.class);
        verify(usuarioLogroDAO).save(guardado.capture());
        assertSame(usuario, guardado.getValue().getUsuario());
        assertSame(logro, guardado.getValue().getLogro());

        verify(usuarioMonedaService).registrarMovimiento(
                usuario, 50, "LOGRO", 7, "Logro conseguido: Logro de prueba");
    }

    @Test
    void otorgarSiNoTiene_codigoExistente_concedeElLogro() {
        when(logroDAO.findByCodigo("LOGRO_PRUEBA")).thenReturn(logro);
        when(usuarioLogroDAO.existePorUsuarioYLogro(1, 7)).thenReturn(false);
        when(logroDAO.findById(7)).thenReturn(logro);

        assertTrue(logroService.otorgarSiNoTiene(usuario, "LOGRO_PRUEBA"));

        ArgumentCaptor<UsuarioLogro> guardado = ArgumentCaptor.forClass(UsuarioLogro.class);
        verify(usuarioLogroDAO).save(guardado.capture());
        assertSame(logro, guardado.getValue().getLogro());
        verify(usuarioMonedaService).registrarMovimiento(
                eq(usuario), eq(50), eq("LOGRO"), eq(7), anyString());
    }
}
