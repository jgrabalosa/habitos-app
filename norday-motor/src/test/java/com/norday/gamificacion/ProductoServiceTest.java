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
import java.util.List;
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
    void elegirIdentidad_otorgaTambienElLogroDeEsaIdentidad() {
        // Es la decisión de regalar el primer tema con sus 500 puntos: si
        // alguien quita la llamada a otorgarLogroDeIdentidad, el usuario
        // nuevo se queda sin su primera victoria sin que ningún test se
        // entere.
        Producto profundidad = new Producto();
        profundidad.setProductoId(30);
        profundidad.setCodigo("TEMA_PROFUNDIDAD");
        profundidad.setCategoria("Tema");
        profundidad.setTipo("EQUIPABLE");
        profundidad.setActivo(true);
        profundidad.setNombre("Profundidad");

        when(usuarioProductoDAO.poseeAlgunoDeCategoria(1, "Tema")).thenReturn(false);
        when(productoDAO.findById(30)).thenReturn(profundidad);

        productoService.otorgarIdentidadElegida(usuario, 30);

        verify(logroService).otorgarSiNoTiene(usuario, "IDENTIDAD_PROFUNDIDAD");
    }

    @Test
    void comprarProducto_siNoEsCategoriaTema_noOtorgaLogro() {
        Producto avatar = new Producto();
        avatar.setProductoId(20);
        avatar.setCodigo("AVATAR_ZORRO");
        avatar.setCategoria("Avatar");
        avatar.setTipo("EQUIPABLE");
        avatar.setPrecio(500);
        avatar.setActivo(true);

        when(productoDAO.findById(20)).thenReturn(avatar);
        when(usuarioProductoDAO.findByUsuarioYProducto(1, 20)).thenReturn(null);
        when(usuarioMonedaService.consultarSaldo(1)).thenReturn(1000);

        productoService.comprarProducto(usuario, 20);

        verifyNoInteractions(logroService);
    }

    @Test
    void comprarProducto_siEsUnTemaYSeOtorgaElLogro_loDevuelve() {
        Producto profundidad = new Producto();
        profundidad.setProductoId(30);
        profundidad.setCodigo("TEMA_PROFUNDIDAD");
        profundidad.setCategoria("Tema");
        profundidad.setTipo("EQUIPABLE");
        profundidad.setPrecio(500);
        profundidad.setActivo(true);
        profundidad.setNombre("Profundidad");

        when(productoDAO.findById(30)).thenReturn(profundidad);
        when(usuarioProductoDAO.findByUsuarioYProducto(1, 30)).thenReturn(null);
        when(usuarioMonedaService.consultarSaldo(1)).thenReturn(1000);
        when(logroService.otorgarSiNoTiene(usuario, "IDENTIDAD_PROFUNDIDAD")).thenReturn(true);

        List<String> logrosOtorgados = productoService.comprarProducto(usuario, 30);

        assertEquals(List.of("IDENTIDAD_PROFUNDIDAD"), logrosOtorgados);
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
