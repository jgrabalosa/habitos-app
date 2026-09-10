package com.norday.habitos;

import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.dto.HabitoDTO;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** La categoria sale aplanada: el movil lee estos tres campos. */
class HabitoDTOTest {

    @Test
    void conCategoria_copiaIdCodigoYNombre() {
        Categoria categoria = new Categoria();
        categoria.setCategoriaId(3);
        categoria.setCodigo("SALUD");
        categoria.setNombre("Salud");
        Habito habito = new Habito();
        habito.setHabitoId(10);
        habito.setFrecuencia(Frecuencia.DIARIO);
        habito.setTipo(categoria);

        HabitoDTO dto = HabitoDTO.desde(habito);

        assertEquals(3, dto.getCategoriaId());
        assertEquals("SALUD", dto.getCategoriaCodigo());
        assertEquals("Salud", dto.getCategoriaNombre());
    }

    @Test
    void sinCategoria_losTresCamposSonNull() {
        Habito habito = new Habito();
        habito.setHabitoId(10);
        habito.setFrecuencia(Frecuencia.DIARIO);

        HabitoDTO dto = HabitoDTO.desde(habito);

        assertNull(dto.getCategoriaId());
        assertNull(dto.getCategoriaCodigo());
        assertNull(dto.getCategoriaNombre());
    }
}
