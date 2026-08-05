package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.config.CategoriasInitializer;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.service.CategoriaService;
import com.norday.habitos.service.LogrosHabitosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cierra el pendiente que arrastrábamos: que arrancar dos veces no duplique
 * la siembra. Antes no se podía comprobar sin una base de datos; con la
 * guarda por código sí, simulando el estado de la tabla.
 */
@ExtendWith(MockitoExtension.class)
class CategoriasInitializerTest {

    @Mock
    private ICategoriaDAO categoriaDAO;

    @InjectMocks
    private CategoriasInitializer initializer;

    /** Simula la tabla: lo que se guarda queda visible para findByCodigo. */
    private Map<String, Categoria> simularTabla() {
        Map<String, Categoria> tabla = new HashMap<>();
        when(categoriaDAO.findByCodigo(anyString()))
                .thenAnswer(inv -> tabla.get(inv.getArgument(0, String.class)));
        lenient().doAnswer(inv -> {
            Categoria c = inv.getArgument(0, Categoria.class);
            tabla.put(c.getCodigo(), c);
            return null;
        }).when(categoriaDAO).save(any(Categoria.class));
        return tabla;
    }

    @Test
    void primerArranque_siembraLasDiezCategoriasGlobales() {
        Map<String, Categoria> tabla = simularTabla();

        initializer.run();

        assertEquals(10, tabla.size());
        verify(categoriaDAO, times(10)).save(any(Categoria.class));
    }

    @Test
    void segundoArranque_noDuplicaNada() {
        Map<String, Categoria> tabla = simularTabla();

        initializer.run();
        initializer.run();

        assertEquals(10, tabla.size());
        // Diez guardados en total: el segundo arranque no escribe ni uno más
        verify(categoriaDAO, times(10)).save(any(Categoria.class));
    }

    @Test
    void todasLasGlobalesLlevanCodigoYSonGlobales() {
        Map<String, Categoria> tabla = simularTabla();

        initializer.run();

        assertTrue(tabla.values().stream().allMatch(c -> c.getCodigo() != null));
        assertTrue(tabla.values().stream().allMatch(Categoria::isEsGlobal));
        assertTrue(tabla.containsKey("CAT_SALUD"));
        assertTrue(tabla.containsKey("CAT_SUENO"));
    }
}
