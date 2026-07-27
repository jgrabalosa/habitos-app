package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.service.CategoriaService;
import com.norday.habitos.service.LogrosHabitosService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CategoriaServiceTest {

    @Mock
    private ICategoriaDAO categoriaDAO;

    @Mock
    private LogrosHabitosService logrosHabitosService;

    @InjectMocks
    private CategoriaService categoriaService;

    @Test
    void unaCategoriaDelUsuarioNuncaSeQuedaConCodigo() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        // El cliente manda la entidad entera: podría colar un código del catálogo
        Categoria intento = new Categoria();
        intento.setNombre("Mis cosas");
        intento.setCodigo("CAT_SALUD");
        intento.setEsGlobal(true);
        intento.setCreador(usuario);

        when(categoriaDAO.findByCreador(usuario)).thenReturn(new ArrayList<>());

        categoriaService.crearCategoria(intento);

        // El código pertenece a la siembra, no al cliente: se ignora
        assertNull(intento.getCodigo());
        assertFalse(intento.isEsGlobal());
        verify(categoriaDAO).save(intento);
    }
}
