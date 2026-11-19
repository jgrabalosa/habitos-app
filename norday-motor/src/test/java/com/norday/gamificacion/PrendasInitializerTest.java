package com.norday.gamificacion;

import com.norday.gamificacion.config.CatalogoGamificacionInitializer;
import com.norday.gamificacion.model.Oferta;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IOfertaDAO;
import com.norday.gamificacion.repository.IProductoDAO;
import com.norday.gamificacion.service.RelojTienda;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La fecha de inicio de las ofertas de las prendas (N-13), con el reloj
 * fijado: el día anterior al del alta en UTC y, si el alta llega después de
 * que acabe una temporada, una oferta de un día en su último día (nunca
 * desde > hasta, que el CHECK de la tabla rechazaría al arrancar).
 */
@ExtendWith(MockitoExtension.class)
class PrendasInitializerTest {

    @Mock private ILogroDAO logroDAO;
    @Mock private IProductoDAO productoDAO;
    @Mock private IOfertaDAO ofertaDAO;
    @Mock private RelojTienda reloj;
    @InjectMocks private CatalogoGamificacionInitializer inicializador;

    private Map<String, Oferta> arrancarEl(String instante) {
        when(reloj.ahora()).thenReturn(Instant.parse(instante));
        when(logroDAO.findAll()).thenReturn(List.of());
        when(productoDAO.findAll()).thenReturn(List.of());
        when(ofertaDAO.deProducto(anyInt())).thenReturn(List.of());
        inicializador.run();
        ArgumentCaptor<Oferta> ofertas = ArgumentCaptor.forClass(Oferta.class);
        verify(ofertaDAO, times(4)).save(ofertas.capture());
        return ofertas.getAllValues().stream()
                .collect(Collectors.toMap(o -> o.getProducto().getCodigo(), o -> o));
    }

    @Test
    void empiezanElDiaAnteriorAlDelAltaEnUTC() {
        Map<String, Oferta> ofertas = arrancarEl("2026-11-20T00:30:00Z");
        ofertas.values().forEach(o -> assertEquals(LocalDate.of(2026, 11, 19), o.getDesde(),
                o.getProducto().getCodigo()));
        assertEquals(0, ofertas.get("PRENDA_GORRO_NOEL").getPrecio());
        assertEquals(LocalDate.of(2027, 1, 7), ofertas.get("PRENDA_TRAJE_NOEL").getHasta());
        assertEquals(LocalDate.of(2027, 2, 28), ofertas.get("PRENDA_BUFANDA").getHasta());
    }

    @Test
    void unAltaDespuesDeLaTemporada_noDaDesdeMayorQueHasta() {
        Map<String, Oferta> ofertas = arrancarEl("2027-03-05T12:00:00Z");
        ofertas.values().forEach(o -> assertFalse(o.getDesde().isAfter(o.getHasta()),
                o.getProducto().getCodigo()));
        assertEquals(LocalDate.of(2027, 1, 7), ofertas.get("PRENDA_GORRO_NOEL").getDesde());
        assertEquals(LocalDate.of(2027, 2, 28), ofertas.get("PRENDA_GORRO_LANA").getDesde());
    }
}
