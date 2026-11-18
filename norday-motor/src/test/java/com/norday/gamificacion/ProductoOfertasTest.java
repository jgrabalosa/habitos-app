package com.norday.gamificacion;

import com.norday.core.exception.ConflictoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.model.dto.OfertaDTO;
import com.norday.gamificacion.model.dto.ProductoCatalogoDTO;
import com.norday.gamificacion.repository.IOfertaDAO;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.gamificacion.service.RelojTienda;
import com.norday.gamificacion.service.UsuarioMonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Las ofertas por periodo (Nori vestida, N-4): qué precio se cobra cada día,
 * el regalo, «Fuera de temporada» y qué ofertas salen en el catálogo. El día
 * de la compra es el de la zona del usuario; el del catálogo, el de UTC.
 */
@ExtendWith(MockitoExtension.class)
class ProductoOfertasTest {

    private static final LocalDate D1 = LocalDate.of(2026, 12, 1);
    private static final LocalDate D6E = LocalDate.of(2027, 1, 6);

    @Mock private IProductoDAO productoDAO;
    @Mock private IUsuarioProductoDAO usuarioProductoDAO;
    @Mock private UsuarioMonedaService usuarioMonedaService;
    @Mock private MascotaService mascotaService;
    @Mock private LogroService logroService;
    @Mock private IUsuarioDAO usuarioDAO;
    @Mock private IOfertaDAO ofertaDAO;
    @Mock private RelojTienda reloj;
    @Spy private ZonaUsuarioService zonaUsuarioService = new ZonaUsuarioService();

    @InjectMocks
    private ProductoService productoService;

    private Usuario usuario;
    private Producto gorro;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        usuario.setZonaHoraria("Europe/Madrid");
        lenient().when(usuarioDAO.bloquear(anyInt())).thenReturn(true);

        gorro = producto(40, "PRENDA_GORRO_NOEL", "Cabeza", "EQUIPABLE", 250);
        lenient().when(productoDAO.findById(40)).thenReturn(gorro);
    }

    private static Producto producto(int id, String codigo, String categoria, String tipo, int precio) {
        Producto p = new Producto();
        p.setProductoId(id);
        p.setCodigo(codigo);
        p.setNombre(codigo);
        p.setCategoria(categoria);
        p.setTipo(tipo);
        p.setPrecio(precio);
        p.setActivo(true);
        return p;
    }

    /** El reloj, a las 12:00 UTC de ese día. */
    private void hoy(LocalDate dia) {
        ahora(Instant.parse(dia + "T12:00:00Z"));
    }

    private void ahora(Instant instante) {
        when(reloj.ahora()).thenReturn(instante);
    }

    private void ofertas(int productoId, OfertaDTO... ofertas) {
        when(ofertaDAO.deProducto(productoId)).thenReturn(List.of(ofertas));
    }

    private static OfertaDTO oferta(int productoId, LocalDate desde, LocalDate hasta, int precio) {
        return new OfertaDTO(productoId, desde, hasta, precio);
    }

    private void saldo(int saldo) {
        lenient().when(usuarioMonedaService.consultarSaldo(1)).thenReturn(saldo);
    }

    // ── La compra ──────────────────────────────────────────

    @Test
    void sinOfertas_cobraSuPrecio_comoHoy_yNoMiraElReloj() {
        Producto tema = producto(10, "TEMA_DULCE", "Tema", "EQUIPABLE", 1000);
        when(productoDAO.findById(10)).thenReturn(tema);
        when(ofertaDAO.deProducto(10)).thenReturn(List.of());
        saldo(1000);

        productoService.comprarProducto(usuario, 10);

        verify(usuarioMonedaService).registrarMovimiento(usuario, -1000, "COMPRA", 10, "Compra: TEMA_DULCE");
        verifyNoInteractions(reloj);
    }

    @Test
    void sinOfertas_unaComidaSeSumaComoHoy() {
        Producto comida = producto(20, "COMIDA_BASICA", "Comida", "CONSUMIBLE", 50);
        when(productoDAO.findById(20)).thenReturn(comida);
        when(ofertaDAO.deProducto(20)).thenReturn(List.of());
        UsuarioProducto tiene = new UsuarioProducto(usuario, comida, 2);
        when(usuarioProductoDAO.findByUsuarioYProducto(1, 20)).thenReturn(tiene);
        saldo(50);

        productoService.comprarProducto(usuario, 20);

        assertEquals(3, tiene.getCantidad());
        verify(usuarioMonedaService).registrarMovimiento(usuario, -50, "COMPRA", 20, "Compra: COMIDA_BASICA");
    }

    @Test
    void sinOfertas_sinSaldo_siguenDiciendoSaldoInsuficiente() {
        Producto tema = producto(10, "TEMA_DULCE", "Tema", "EQUIPABLE", 1000);
        when(productoDAO.findById(10)).thenReturn(tema);
        saldo(999);

        RuntimeException e = assertThrows(RuntimeException.class, () -> productoService.comprarProducto(usuario, 10));
        assertEquals("Saldo insuficiente", e.getMessage());
    }

    @Test
    void dentroDeLaOferta_cobraElPrecioDeLaOferta_noElDelProducto() {
        ofertas(40, oferta(40, D1, D6E, 120));
        hoy(LocalDate.of(2026, 12, 15));
        saldo(500);

        productoService.comprarProducto(usuario, 40);

        verify(usuarioMonedaService).registrarMovimiento(usuario, -120, "COMPRA", 40, "Compra: PRENDA_GORRO_NOEL");
        verify(usuarioProductoDAO).save(any(UsuarioProducto.class));
    }

    @Test
    void elPrimerYElUltimoDia_cuentan() {
        ofertas(40, oferta(40, D1, D6E, 120));
        saldo(500);

        hoy(D1);
        productoService.comprarProducto(usuario, 40);
        hoy(D6E);
        productoService.comprarProducto(usuario, 40);

        verify(usuarioMonedaService, times(2)).registrarMovimiento(usuario, -120, "COMPRA", 40, "Compra: PRENDA_GORRO_NOEL");
    }

    @Test
    void unDiaAntes_fueraDeTemporada_sinTocarSaldoNiInventario() {
        ofertas(40, oferta(40, D1, D6E, 120));
        hoy(D1.minusDays(1));
        saldo(500);

        ConflictoException e = assertThrows(ConflictoException.class, () -> productoService.comprarProducto(usuario, 40));

        assertEquals("Fuera de temporada", e.getMessage());
        verify(usuarioMonedaService, never()).registrarMovimiento(any(), anyInt(), anyString(), anyInt(), anyString());
        verify(usuarioProductoDAO, never()).save(any());
    }

    @Test
    void unDiaDespues_fueraDeTemporada() {
        ofertas(40, oferta(40, D1, D6E, 120));
        hoy(D6E.plusDays(1));

        ConflictoException e = assertThrows(ConflictoException.class, () -> productoService.comprarProducto(usuario, 40));

        assertEquals("Fuera de temporada", e.getMessage());
        verify(usuarioProductoDAO, never()).save(any());
    }

    /**
     * 1-dic a las 00:30 en Madrid es el 30-nov a las 17:30 en Ciudad de
     * México: a la misma hora UTC, uno ya está dentro y el otro aún no.
     */
    @Test
    void elDiaDelCambio_esElDeLaZonaDelUsuario() {
        ofertas(40, oferta(40, D1, D6E, 120));
        ahora(Instant.parse("2026-11-30T23:30:00Z"));
        saldo(500);

        productoService.comprarProducto(usuario, 40);
        verify(usuarioMonedaService).registrarMovimiento(usuario, -120, "COMPRA", 40, "Compra: PRENDA_GORRO_NOEL");

        Usuario mexicano = new Usuario();
        mexicano.setUsuarioId(2);
        mexicano.setZonaHoraria("America/Mexico_City");
        ConflictoException e = assertThrows(ConflictoException.class,
                () -> productoService.comprarProducto(mexicano, 40));
        assertEquals("Fuera de temporada", e.getMessage());
    }

    @Test
    void regalo_conSaldoCero_seCompra_yElMovimientoEsDe0ConOrigenRegalo() {
        ofertas(40, oferta(40, D1, D6E, 0));
        hoy(D1);
        saldo(0);

        productoService.comprarProducto(usuario, 40);

        verify(usuarioMonedaService).registrarMovimiento(usuario, 0, "REGALO", 40, "Regalo: PRENDA_GORRO_NOEL");
        verify(usuarioProductoDAO).save(any(UsuarioProducto.class));
    }

    @Test
    void regalo_conSaldoNegativo_seCompraIgual_sinMirarElSaldo() {
        ofertas(40, oferta(40, D1, D6E, 0));
        hoy(D1);
        saldo(-5);

        productoService.comprarProducto(usuario, 40);

        verify(usuarioMonedaService, never()).consultarSaldo(anyInt());
        verify(usuarioMonedaService).registrarMovimiento(usuario, 0, "REGALO", 40, "Regalo: PRENDA_GORRO_NOEL");
    }

    @Test
    void regalo_siYaLoTiene_noSeDaDosVeces() {
        // «Ya tienes este producto» va antes de mirar la temporada.
        when(usuarioProductoDAO.findByUsuarioYProducto(1, 40)).thenReturn(new UsuarioProducto(usuario, gorro, 1));

        ConflictoException e = assertThrows(ConflictoException.class, () -> productoService.comprarProducto(usuario, 40));

        assertEquals("Ya tienes este producto", e.getMessage());
        verify(usuarioMonedaService, never()).registrarMovimiento(any(), anyInt(), anyString(), anyInt(), anyString());
    }

    @Test
    void dosOfertasSeguidas_cadaDiaElPrecioQueToca() {
        ofertas(40,
                oferta(40, D1, D1.plusDays(6), 0),
                oferta(40, D1.plusDays(7), D6E, 200));
        saldo(500);

        hoy(D1.plusDays(6));
        productoService.comprarProducto(usuario, 40);
        hoy(D1.plusDays(7));
        productoService.comprarProducto(usuario, 40);

        verify(usuarioMonedaService).registrarMovimiento(usuario, 0, "REGALO", 40, "Regalo: PRENDA_GORRO_NOEL");
        verify(usuarioMonedaService).registrarMovimiento(usuario, -200, "COMPRA", 40, "Compra: PRENDA_GORRO_NOEL");
    }

    @Test
    void ofertasSolapadas_mandaLaDeDesdeMasReciente() {
        ofertas(40,
                oferta(40, D1, D6E, 250),
                oferta(40, D1.plusDays(10), D1.plusDays(12), 100));
        hoy(D1.plusDays(11));
        saldo(500);

        productoService.comprarProducto(usuario, 40);

        verify(usuarioMonedaService).registrarMovimiento(usuario, -100, "COMPRA", 40, "Compra: PRENDA_GORRO_NOEL");
    }

    @Test
    void dentroDeLaOferta_sinSaldoParaSuPrecio_saldoInsuficiente() {
        ofertas(40, oferta(40, D1, D6E, 120));
        hoy(D1);
        saldo(119);

        RuntimeException e = assertThrows(RuntimeException.class, () -> productoService.comprarProducto(usuario, 40));

        assertEquals("Saldo insuficiente", e.getMessage());
        verify(usuarioProductoDAO, never()).save(any());
    }

    @Test
    void equipar_unaPrendaCompradaFueraDeFechas_funciona_yNoMiraFechas() {
        UsuarioProducto tiene = new UsuarioProducto(usuario, gorro, 1);
        when(usuarioProductoDAO.findByUsuarioYProducto(1, 40)).thenReturn(tiene);

        productoService.equiparProducto(usuario, 40);

        assertTrue(tiene.isEquipado());
        verifyNoInteractions(reloj, ofertaDAO);
    }

    // ── El catálogo ────────────────────────────────────────

    @Test
    void catalogo_unProductoConTodasSusOfertasTerminadas_noSale() {
        Producto tema = producto(10, "TEMA_DULCE", "Tema", "EQUIPABLE", 1000);
        when(productoDAO.findActivosParaApp("habitos")).thenReturn(List.of(tema, gorro));
        when(ofertaDAO.deProductos(List.of(10, 40))).thenReturn(List.of(oferta(40, D1, D6E, 0)));
        hoy(D6E.plusDays(1));

        List<ProductoCatalogoDTO> catalogo = productoService.catalogoConOfertas("habitos");

        assertEquals(List.of("TEMA_DULCE"), catalogo.stream().map(p -> p.getProducto().getCodigo()).toList());
        assertEquals(List.of(), catalogo.get(0).getOfertas());
    }

    @Test
    void catalogo_conUnaOfertaFutura_sale_conSusOfertasOrdenadasPorDesde() {
        when(productoDAO.findActivosParaApp("habitos")).thenReturn(List.of(gorro));
        OfertaDTO segunda = oferta(40, D1.plusDays(7), D6E, 200);
        OfertaDTO primera = oferta(40, D1, D1.plusDays(6), 0);
        when(ofertaDAO.deProductos(List.of(40))).thenReturn(List.of(segunda, primera));
        hoy(LocalDate.of(2026, 11, 20));

        List<ProductoCatalogoDTO> catalogo = productoService.catalogoConOfertas("habitos");

        assertEquals(1, catalogo.size());
        assertEquals(List.of(primera, segunda), catalogo.get(0).getOfertas());
    }

    @Test
    void catalogo_lasOfertasTerminadasNoSalen_lasQueSiguenSi() {
        when(productoDAO.findActivosParaApp("habitos")).thenReturn(List.of(gorro));
        OfertaDTO terminada = oferta(40, D1, D1.plusDays(6), 0);
        OfertaDTO abierta = oferta(40, D1.plusDays(7), D6E, 200);
        when(ofertaDAO.deProductos(List.of(40))).thenReturn(List.of(terminada, abierta));
        hoy(D1.plusDays(7));

        List<ProductoCatalogoDTO> catalogo = productoService.catalogoConOfertas("habitos");

        assertEquals(List.of(abierta), catalogo.get(0).getOfertas());
    }

    @Test
    void catalogo_elUltimoDiaEnUtc_laOfertaSigue() {
        when(productoDAO.findActivosParaApp("habitos")).thenReturn(List.of(gorro));
        when(ofertaDAO.deProductos(List.of(40))).thenReturn(List.of(oferta(40, D1, D6E, 0)));
        ahora(Instant.parse("2027-01-06T23:59:00Z"));

        assertEquals(1, productoService.catalogoConOfertas("habitos").size());
    }

    @Test
    void catalogo_sinOfertas_listaVacia_yNoMiraElReloj() {
        when(productoDAO.findActivos()).thenReturn(List.of(gorro));
        when(ofertaDAO.deProductos(List.of(40))).thenReturn(List.of());

        List<ProductoCatalogoDTO> catalogo = productoService.catalogoConOfertas(null);

        assertEquals(1, catalogo.size());
        assertEquals(List.of(), catalogo.get(0).getOfertas());
        verifyNoInteractions(reloj);
    }

    @Test
    void catalogo_lasOfertasSeLeenEnUnaSolaConsulta() {
        Producto tema = producto(10, "TEMA_DULCE", "Tema", "EQUIPABLE", 1000);
        Producto comida = producto(20, "COMIDA_BASICA", "Comida", "CONSUMIBLE", 50);
        when(productoDAO.findActivosParaApp("enfoque")).thenReturn(List.of(tema, comida, gorro));
        when(ofertaDAO.deProductos(anyCollection())).thenReturn(List.of());

        productoService.catalogoConOfertas("enfoque");

        verify(ofertaDAO, times(1)).deProductos(anyCollection());
        verify(ofertaDAO, never()).deProducto(anyInt());
    }
}
