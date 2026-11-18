package com.norday.gamificacion;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.dto.ProductoCatalogoDTO;
import com.norday.gamificacion.repository.IOfertaDAO;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioEquipadoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.gamificacion.service.RelojTienda;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.gamificacion.service.VersionCore;
import com.norday.core.service.ZonaUsuarioService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * El catálogo según la versión del core (Nori vestida, N-6): un producto con
 * versión mínima solo lo ve y lo compra un cliente cuya cabecera
 * X-Norday-Core se lee y la alcanza.
 */
@ExtendWith(MockitoExtension.class)
class VersionCoreTest {

    @Mock private IProductoDAO productoDAO;
    @Mock private IUsuarioProductoDAO usuarioProductoDAO;
    @Mock private UsuarioMonedaService usuarioMonedaService;
    @Mock private MascotaService mascotaService;
    @Mock private LogroService logroService;
    @Mock private IUsuarioDAO usuarioDAO;
    @Mock private IOfertaDAO ofertaDAO;
    @Mock private IUsuarioEquipadoDAO usuarioEquipadoDAO;
    @Mock private RelojTienda reloj;
    @Mock private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private ProductoService productoService;

    private Producto tema;
    private Producto gorro;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        tema = producto(10, "TEMA_DULCE", null);
        gorro = producto(40, "PRENDA_GORRO_NOEL", "0.56.0");
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        lenient().when(usuarioDAO.bloquear(1)).thenReturn(true);
        lenient().when(productoDAO.findActivosParaApp(anyString())).thenReturn(List.of(tema, gorro));
        lenient().when(productoDAO.findActivos()).thenReturn(List.of(tema, gorro));
    }

    private static Producto producto(int id, String codigo, String minima) {
        Producto p = new Producto();
        p.setProductoId(id);
        p.setCodigo(codigo);
        p.setNombre(codigo);
        p.setCategoria("Cabeza");
        p.setTipo("EQUIPABLE");
        p.setPrecio(0);
        p.setActivo(true);
        p.setVersionMinimaCore(minima);
        return p;
    }

    private List<String> codigos(String app, String version) {
        return productoService.catalogoConOfertas(app, version).stream()
                .map(ProductoCatalogoDTO::getProducto).map(Producto::getCodigo).toList();
    }

    // ── La comparación ─────────────────────────────────────

    @Test
    void sinMinima_siempre() {
        assertTrue(VersionCore.alcanza(null, null));
        assertTrue(VersionCore.alcanza("cualquier cosa", ""));
    }

    @Test
    void menorIgualYMayor() {
        assertFalse(VersionCore.alcanza("0.55.0", "0.56.0"));
        assertTrue(VersionCore.alcanza("0.56.0", "0.56.0"));
        assertTrue(VersionCore.alcanza("0.56.1", "0.56.0"));
        assertTrue(VersionCore.alcanza("1.0.0", "0.56.0"));
        assertFalse(VersionCore.alcanza("0.55.9", "0.56.0"));
    }

    @Test
    void porNumeros_noPorTexto() {
        assertTrue(VersionCore.alcanza("0.10.0", "0.9.0"));
        assertFalse(VersionCore.alcanza("0.9.0", "0.10.0"));
    }

    @Test
    void ilegible_noAlcanza() {
        for (String mala : new String[]{null, "", "0.56", "0.56.0+1", "0.56.0-beta", "v0.56.0", "a.b.c"}) {
            assertFalse(VersionCore.alcanza(mala, "0.56.0"), String.valueOf(mala));
        }
        assertTrue(VersionCore.alcanza(" 0.56.0 ", "0.56.0"), "los espacios de alrededor no cuentan");
    }

    @Test
    void unaMinimaIlegible_noSeVe() {
        assertFalse(VersionCore.alcanza("0.56.0", "0.56"));
    }

    // ── El catálogo ────────────────────────────────────────

    @Test
    void catalogo_sinCabecera_noSaleElDeMinima_yElRestoSi() {
        assertEquals(List.of("TEMA_DULCE"), codigos("habitos", null));
        assertEquals(List.of("TEMA_DULCE"), codigos(null, null));
    }

    @Test
    void catalogo_conVersionMenor_noSale() {
        assertEquals(List.of("TEMA_DULCE"), codigos("enfoque", "0.55.0"));
    }

    @Test
    void catalogo_conVersionIgualOMayor_sale() {
        assertEquals(List.of("TEMA_DULCE", "PRENDA_GORRO_NOEL"), codigos("habitos", "0.56.0"));
        assertEquals(List.of("TEMA_DULCE", "PRENDA_GORRO_NOEL"), codigos("habitos", "0.57.2"));
    }

    @Test
    void catalogo_conVersionIlegible_noSale() {
        assertEquals(List.of("TEMA_DULCE"), codigos("habitos", "0.56.0+1"));
    }

    // ── La compra ──────────────────────────────────────────

    @Test
    void compra_sinCabecera_actualizaLaApp_sinTocarNada() {
        when(productoDAO.findById(40)).thenReturn(gorro);

        ConflictoException e = assertThrows(ConflictoException.class,
                () -> productoService.comprarProducto(usuario, 40, null));

        assertEquals("Actualiza la app", e.getMessage());
        verify(usuarioProductoDAO, never()).save(any());
        verify(usuarioMonedaService, never()).registrarMovimiento(any(), anyInt(), anyString(), anyInt(), anyString());
    }

    @Test
    void compra_conVersionMenor_actualizaLaApp() {
        when(productoDAO.findById(40)).thenReturn(gorro);

        ConflictoException e = assertThrows(ConflictoException.class,
                () -> productoService.comprarProducto(usuario, 40, "0.55.0"));

        assertEquals("Actualiza la app", e.getMessage());
    }

    @Test
    void compra_conLaVersion_sigue() {
        when(productoDAO.findById(40)).thenReturn(gorro);
        when(ofertaDAO.deProducto(40)).thenReturn(List.of());

        productoService.comprarProducto(usuario, 40, "0.56.0");

        verify(usuarioProductoDAO).save(any());
    }

    @Test
    void compra_sinMinima_daIgualLaCabecera() {
        when(productoDAO.findById(10)).thenReturn(tema);
        when(ofertaDAO.deProducto(10)).thenReturn(List.of());

        productoService.comprarProducto(usuario, 10);

        verify(usuarioProductoDAO).save(any());
    }
}
