package com.norday;

import com.norday.model.Usuario;
import com.norday.repository.IUsuarioMonedaDAO;
import com.norday.service.UsuarioMonedaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class UsuarioMonedaServiceTest {

    @Mock
    private IUsuarioMonedaDAO usuarioMonedaDAO;

    @InjectMocks
    private UsuarioMonedaService usuarioMonedaService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
    }

    @Test
    void elSaldoEsLaSumaDeTodosLosMovimientos() {
        when(usuarioMonedaDAO.calcularSaldo(1)).thenReturn(350);

        int saldo = usuarioMonedaService.consultarSaldo(1);

        assertEquals(350, saldo);
    }

    @Test
    void unUsuarioSinMovimientos_tieneSaldoCero() {
        when(usuarioMonedaDAO.calcularSaldo(1)).thenReturn(0);

        int saldo = usuarioMonedaService.consultarSaldo(1);

        assertEquals(0, saldo);
    }
}