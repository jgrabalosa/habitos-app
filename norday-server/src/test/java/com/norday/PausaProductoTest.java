package com.norday;

import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.repository.IProductoDAO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.junit.jupiter.api.Assertions.*;

/** Al arrancar, el catálogo tiene la pausa como consumible propio de hábitos. */
@SpringBootTest
class PausaProductoTest {

    @Autowired
    private IProductoDAO productoDAO;

    @Test
    void alArrancar_existeLaPausaComoConsumibleDeHabitos() {
        Producto pausa = productoDAO.findByCodigo("PAUSA_HABITO");
        assertNotNull(pausa);
        assertEquals("CONSUMIBLE", pausa.getTipo());
        assertEquals("Protección", pausa.getCategoria());
        assertEquals("habitos", pausa.getOrigenApp());
        assertEquals(300, pausa.getPrecio());
        assertTrue(pausa.isActivo());
    }
}
