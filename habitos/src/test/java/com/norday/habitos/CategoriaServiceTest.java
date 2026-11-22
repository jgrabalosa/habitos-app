package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.core.service.TextosService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.dto.CategoriaEntradaDTO;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.service.CategoriaService;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.LogrosHabitosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private ICategoriaDAO categoriaDAO;

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private LogrosHabitosService logrosHabitosService;

    // Bloquear al usuario antes de conceder logros: aquí no hace nada.
    @Mock
    private HabitoService habitoService;

    @Mock
    private TextosService textosService;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void unaCategoriaDelUsuarioNuncaSeQuedaConCodigoNiGlobal() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        when(categoriaDAO.findByCreador(usuario)).thenReturn(new ArrayList<>());

        categoriaService.crear(usuario, new CategoriaEntradaDTO("  Mis cosas  ", "🍎"));

        // Lo que se guarda, no el objeto del test: ArgumentCaptor sobre el save.
        ArgumentCaptor<Categoria> guardada = ArgumentCaptor.forClass(Categoria.class);
        verify(categoriaDAO).save(guardada.capture());
        assertNull(guardada.getValue().getCodigo());
        assertFalse(guardada.getValue().isEsGlobal());
        assertEquals("Mis cosas", guardada.getValue().getNombre());
        assertEquals("🍎", guardada.getValue().getIcono());
        assertSame(usuario, guardada.getValue().getCreador());
    }

    @Test
    void bloqueaAlUsuarioAntesDeEscribir() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        when(categoriaDAO.findByCreador(usuario)).thenReturn(new ArrayList<>());

        categoriaService.crear(usuario, new CategoriaEntradaDTO("Mis cosas", null));

        InOrder orden = inOrder(habitoService, categoriaDAO);
        orden.verify(habitoService).bloquearUsuario(usuario);
        orden.verify(categoriaDAO).save(any(Categoria.class));
    }

    @Test
    void borrarQuitaLaCategoriaALosHabitosAntesDeBorrarla() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);
        Categoria categoria = new Categoria(null, "Comer", null, null, null, false, 0, usuario);
        categoria.setCategoriaId(7);
        when(categoriaDAO.findById(7)).thenReturn(categoria);

        categoriaService.eliminar(7);

        InOrder orden = inOrder(habitoService, habitoDAO, categoriaDAO);
        orden.verify(habitoService).bloquearUsuario(usuario);
        orden.verify(habitoDAO).quitarCategoria(7);
        orden.verify(categoriaDAO).delete(7);
    }
}
