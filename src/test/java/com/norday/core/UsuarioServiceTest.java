package com.norday.core;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.LimpiadorDatosUsuario;
import com.norday.core.service.UsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UsuarioServiceTest {

    @Mock
    private IUsuarioDAO usuarioDAO;

    @Mock
    private LimpiadorDatosUsuario limpiadorA;

    @Mock
    private LimpiadorDatosUsuario limpiadorB;

    @InjectMocks
    private UsuarioService usuarioService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        // @InjectMocks no sabe rellenar un List<LimpiadorDatosUsuario>: se
        // inyecta a mano, igual que hace Spring al recolectar las
        // implementaciones registradas.
        ReflectionTestUtils.setField(usuarioService, "limpiadores",
                List.of(limpiadorA, limpiadorB));
    }

    @Test
    void alEliminarCuenta_seInvocanTodosLosLimpiadores() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        usuarioService.eliminarCuenta(1);

        verify(limpiadorA).limpiar(usuario);
        verify(limpiadorB).limpiar(usuario);
    }

    @Test
    void alEliminarCuenta_elUsuarioSeBorraDespuesDeTodosLosLimpiadores() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        usuarioService.eliminarCuenta(1);

        InOrder orden = inOrder(limpiadorA, limpiadorB, usuarioDAO);
        orden.verify(limpiadorA).limpiar(usuario);
        orden.verify(limpiadorB).limpiar(usuario);
        orden.verify(usuarioDAO).delete(1);
    }

    @Test
    void siElUsuarioNoExiste_noSeLimpiaNadaNiSeBorra() {
        when(usuarioDAO.findById(99)).thenReturn(null);

        assertThrows(RuntimeException.class, () -> usuarioService.eliminarCuenta(99));

        verify(limpiadorA, never()).limpiar(any());
        verify(limpiadorB, never()).limpiar(any());
        verify(usuarioDAO, never()).delete(anyInt());
    }
}
