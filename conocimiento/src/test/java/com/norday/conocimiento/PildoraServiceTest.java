package com.norday.conocimiento;

import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.repository.IPildoraDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.conocimiento.repository.IValoracionPildoraDAO;
import com.norday.conocimiento.service.PildoraService;
import com.norday.conocimiento.service.PreferenciaCategoriaService;
import com.norday.core.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PildoraServiceTest {

    @Mock
    private IPildoraDAO pildoraDAO;

    @Mock
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    @Mock
    private IValoracionPildoraDAO valoracionDAO;

    @Mock
    private PreferenciaCategoriaService preferenciaCategoriaService;

    @InjectMocks
    private PildoraService pildoraService;

    @Test
    void elPrimerDescarteCreaLaFilaConUnStrike() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        Pildora pildora = pildoraActiva(50);

        when(pildoraDAO.findById(50)).thenReturn(pildora);
        when(usuarioPildoraDAO.findByUsuarioYPildora(usuario, 50)).thenReturn(null);

        pildoraService.descartar(usuario, 50);

        ArgumentCaptor<UsuarioPildora> guardada = ArgumentCaptor.forClass(UsuarioPildora.class);
        verify(usuarioPildoraDAO).save(guardada.capture());
        assertEquals(EstadoPildora.DESCARTADA, guardada.getValue().getEstado());
        assertEquals(1, guardada.getValue().getNumDescartes());
    }

    @Test
    void volverADescartarSumaUnStrike() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        Pildora pildora = pildoraActiva(50);

        UsuarioPildora existente = new UsuarioPildora(
                usuario, pildora, EstadoPildora.DESCARTADA, 1, LocalDateTime.now().minusDays(2));

        when(pildoraDAO.findById(50)).thenReturn(pildora);
        when(usuarioPildoraDAO.findByUsuarioYPildora(usuario, 50)).thenReturn(existente);

        pildoraService.descartar(usuario, 50);

        assertEquals(2, existente.getNumDescartes());
        verify(usuarioPildoraDAO).update(existente);
        verify(usuarioPildoraDAO, never()).save(any());
    }

    @Test
    void descartarAlgoYaLeidoNoLoDeshace() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        Pildora pildora = pildoraActiva(50);

        UsuarioPildora leida = new UsuarioPildora(
                usuario, pildora, EstadoPildora.GUARDADA, 0, LocalDateTime.now());

        when(pildoraDAO.findById(50)).thenReturn(pildora);
        when(usuarioPildoraDAO.findByUsuarioYPildora(usuario, 50)).thenReturn(leida);

        pildoraService.descartar(usuario, 50);

        assertEquals(EstadoPildora.GUARDADA, leida.getEstado());
        assertEquals(0, leida.getNumDescartes());
        verify(usuarioPildoraDAO, never()).update(any());
        verify(usuarioPildoraDAO, never()).save(any());
    }

    @Test
    void noSePuedeGuardarLoQueNoSeHaLeido() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        when(usuarioPildoraDAO.findByUsuarioYPildora(usuario, 50)).thenReturn(null);

        assertThrows(IllegalArgumentException.class,
                () -> pildoraService.guardar(usuario, 50, true));
        verify(preferenciaCategoriaService, never()).sumarAfinidad(any(), anyInt(), anyDouble());
    }

    private Pildora pildoraActiva(int id) {
        Pildora pildora = new Pildora();
        pildora.setPildoraId(id);
        pildora.setActiva(true);
        return pildora;
    }
}
