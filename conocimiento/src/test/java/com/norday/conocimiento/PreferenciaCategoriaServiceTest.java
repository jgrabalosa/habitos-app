package com.norday.conocimiento;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.EstadoPreferenciaCategoria;
import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.conocimiento.model.dto.PreferenciaCategoriaDTO;
import com.norday.conocimiento.repository.ICategoriaConocimientoDAO;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.conocimiento.service.PreferenciaCategoriaService;
import com.norday.core.model.Usuario;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PreferenciaCategoriaServiceTest {

    @Mock
    private ICategoriaConocimientoDAO categoriaDAO;

    @Mock
    private IUsuarioCategoriaPreferenciaDAO preferenciaDAO;

    @InjectMocks
    private PreferenciaCategoriaService preferenciaService;

    @Test
    void sinNingunaCategoriaEnQuiereNoSeGuardaNada() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        List<PreferenciaCategoriaDTO> todasNeutrasOdiadas = List.of(
                dto(1, "NEUTRAL"),
                dto(2, "NO_QUIERE"));

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> preferenciaService.actualizarPreferencias(usuario, todasNeutrasOdiadas));

        assertTrue(error.getMessage().contains("al menos una"));
        // Lo importante no es solo que falle: es que no deje la mitad guardada
        verify(preferenciaDAO, never()).save(any());
        verify(preferenciaDAO, never()).update(any());
    }

    @Test
    void cambiarDeGustosNoBorraLaAfinidadYaGanada() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(7);

        UsuarioCategoriaPreferencia existente = new UsuarioCategoriaPreferencia(
                usuario, categoria, EstadoPreferenciaCategoria.NEUTRAL, LocalDateTime.now());
        existente.setPuntuacionAfinidad(12.5);

        when(preferenciaDAO.findByUsuarioYCategoria(usuario, 7)).thenReturn(existente);

        preferenciaService.actualizarPreferencias(usuario, List.of(dto(7, "QUIERE")));

        assertEquals(EstadoPreferenciaCategoria.QUIERE, existente.getEstado());
        assertEquals(12.5, existente.getPuntuacionAfinidad());
        verify(preferenciaDAO).update(existente);
    }

    @Test
    void unaCategoriaSinFilaSeConsideraNeutral() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(3);
        categoria.setNombre("Economía");

        when(categoriaDAO.findAll()).thenReturn(List.of(categoria));
        when(preferenciaDAO.findByUsuario(usuario)).thenReturn(List.of());

        List<PreferenciaCategoriaDTO> resultado = preferenciaService.obtenerPreferencias(usuario);

        assertEquals(1, resultado.size());
        assertEquals("NEUTRAL", resultado.get(0).getEstado());
    }

    private PreferenciaCategoriaDTO dto(int categoriaId, String estado) {
        PreferenciaCategoriaDTO dto = new PreferenciaCategoriaDTO();
        dto.setCategoriaId(categoriaId);
        dto.setEstado(estado);
        return dto;
    }
}
