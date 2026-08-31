package com.norday.gamificacion;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ProductoServiceTest {

    @Mock
    private IProductoDAO productoDAO;

    @Mock
    private IUsuarioProductoDAO usuarioProductoDAO;

    @Mock
    private UsuarioMonedaService usuarioMonedaService;

    @Mock
    private MascotaService mascotaService;

    @Mock
    private LogroService logroService;

    @InjectMocks
    private ProductoService productoService;

    private Usuario usuario;
    private Producto identidad;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);

        identidad = new Producto();
        identidad.setProductoId(10);
        identidad.setCodigo("TEMA_ALBA");
        identidad.setCategoria("Tema");
        identidad.setTipo("EQUIPABLE");
        identidad.setActivo(true);
        identidad.setNombre("Alba");
    }

    @Test
    void elegirIdentidad_laOtorgaYLaDejaEquipada() {
        when(usuarioProductoDAO.poseeAlgunoDeCategoria(1, "Tema")).thenReturn(false);
        when(productoDAO.findById(10)).thenReturn(identidad);

        productoService.otorgarIdentidadElegida(usuario, 10);

        ArgumentCaptor<UsuarioProducto> captor = ArgumentCaptor.forClass(UsuarioProducto.class);
        verify(usuarioProductoDAO).save(captor.capture());
        UsuarioProducto guardado = captor.getValue();
        assertTrue(guardado.isEquipado());
        assertEquals(identidad, guardado.getProducto());
    }

    @Test
    void elegirIdentidad_siYaTieneUna_lanzaConflicto() {
        when(productoDAO.findById(10)).thenReturn(identidad);
        when(usuarioProductoDAO.poseeAlgunoDeCategoria(1, "Tema")).thenReturn(true);

        assertThrows(ConflictoException.class,
                () -> productoService.otorgarIdentidadElegida(usuario, 10));

        verify(usuarioProductoDAO, never()).save(any());
    }

    @Test
    void elegirIdentidad_siNoEsCategoriaTema_lanzaConflicto() {
        Producto avatar = new Producto();
        avatar.setProductoId(20);
        avatar.setCategoria("Avatar");
        avatar.setActivo(true);
        when(productoDAO.findById(20)).thenReturn(avatar);

        assertThrows(ConflictoException.class,
                () -> productoService.otorgarIdentidadElegida(usuario, 20));

        verify(usuarioProductoDAO, never()).save(any());
    }

    @Test
    void asegurarIdentidad_siYaTieneUna_noHaceNada() {
        when(usuarioProductoDAO.poseeAlgunoDeCategoria(1, "Tema")).thenReturn(true);

        productoService.asegurarIdentidad(usuario);

        verify(usuarioProductoDAO, never()).save(any());
        verify(productoDAO, never()).findByCodigo(any());
    }

    @Test
    void asegurarIdentidad_siFaltaElTemaPorDefecto_noRevientaYNoOtorga() {
        when(usuarioProductoDAO.poseeAlgunoDeCategoria(1, "Tema")).thenReturn(false);
        when(productoDAO.findByCodigo("TEMA_PROFUNDIDAD")).thenReturn(null);

        assertDoesNotThrow(() -> productoService.asegurarIdentidad(usuario));

        verify(usuarioProductoDAO, never()).save(any());
    }
}
