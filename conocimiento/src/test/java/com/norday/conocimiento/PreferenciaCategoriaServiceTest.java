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

    /**
     * El estado malo va el último a propósito: si la validación se hiciera
     * dentro del bucle de guardado, la primera categoría ya estaría escrita
     * cuando reventara la segunda. Cada DAO es su propia transacción, así que
     * eso quedaría guardado a medias sin rollback que lo recoja.
     */
    @Test
    void unEstadoInvalidoAlFinalNoDejaNadaGuardado() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(1);

        UsuarioCategoriaPreferencia existente = new UsuarioCategoriaPreferencia(
                usuario, categoria, EstadoPreferenciaCategoria.NEUTRAL, LocalDateTime.now());
        lenient().when(preferenciaDAO.findByUsuarioYCategoria(usuario, 1)).thenReturn(existente);

        List<PreferenciaCategoriaDTO> conBasuraAlFinal = List.of(
                dto(1, "QUIERE"),
                dto(2, "ME_DA_IGUAL"));

        assertThrows(IllegalArgumentException.class,
                () -> preferenciaService.actualizarPreferencias(usuario, conBasuraAlFinal));

        verify(preferenciaDAO, never()).save(any());
        verify(preferenciaDAO, never()).update(any());
        assertEquals(EstadoPreferenciaCategoria.NEUTRAL, existente.getEstado());
    }

    /** Mismo motivo, con una categoría que no existe en vez de un estado malo. */
    @Test
    void unaCategoriaInexistenteAlFinalNoDejaNadaGuardado() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Categoria categoria = new Categoria();
        categoria.setCategoriaId(1);

        UsuarioCategoriaPreferencia existente = new UsuarioCategoriaPreferencia(
                usuario, categoria, EstadoPreferenciaCategoria.NEUTRAL, LocalDateTime.now());
        when(preferenciaDAO.findByUsuarioYCategoria(usuario, 1)).thenReturn(existente);
        when(preferenciaDAO.findByUsuarioYCategoria(usuario, 999)).thenReturn(null);
        when(categoriaDAO.findById(999)).thenReturn(null);

        List<PreferenciaCategoriaDTO> conCategoriaFantasma = List.of(
                dto(1, "QUIERE"),
                dto(999, "NEUTRAL"));

        assertThrows(IllegalArgumentException.class,
                () -> preferenciaService.actualizarPreferencias(usuario, conCategoriaFantasma));

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
