package com.norday.core;

import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.UltimoAccesoService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import java.time.LocalDate;
import java.time.LocalDateTime;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class UltimoAccesoServiceTest {

    private static final LocalDate HOY = LocalDate.of(2026, 9, 30);

    @Mock
    private IUsuarioDAO usuarioDAO;

    @InjectMocks
    private UltimoAccesoService servicio;

    @Test
    void primeraLlamadaDelDia_escribeUnaVezConFechaNoNula() {
        servicio.anotar(1, HOY);

        verify(usuarioDAO, times(1)).actualizarUltimoAcceso(eq(1), any(LocalDateTime.class));
    }

    @Test
    void segundaLlamadaElMismoDia_noVuelveAEscribir() {
        servicio.anotar(1, HOY);
        servicio.anotar(1, HOY);

        verify(usuarioDAO, times(1)).actualizarUltimoAcceso(eq(1), any(LocalDateTime.class));
    }

    @Test
    void mismoUsuarioDiaSiguiente_escribeDosVeces() {
        servicio.anotar(1, HOY);
        servicio.anotar(1, HOY.plusDays(1));

        verify(usuarioDAO, times(2)).actualizarUltimoAcceso(eq(1), any(LocalDateTime.class));
    }

    @Test
    void dosUsuariosElMismoDia_escribeUnaVezPorCadaUno() {
        servicio.anotar(1, HOY);
        servicio.anotar(2, HOY);

        verify(usuarioDAO, times(1)).actualizarUltimoAcceso(eq(1), any(LocalDateTime.class));
        verify(usuarioDAO, times(1)).actualizarUltimoAcceso(eq(2), any(LocalDateTime.class));
        verify(usuarioDAO, times(2)).actualizarUltimoAcceso(anyInt(), any(LocalDateTime.class));
    }

    @Test
    void siElDaoFalla_noPropagaYLaSiguienteLlamadaReintenta() {
        doThrow(new RuntimeException("BD caída")).doNothing()
                .when(usuarioDAO).actualizarUltimoAcceso(eq(1), any(LocalDateTime.class));

        assertDoesNotThrow(() -> servicio.anotar(1, HOY));
        assertDoesNotThrow(() -> servicio.anotar(1, HOY));

        verify(usuarioDAO, times(2)).actualizarUltimoAcceso(eq(1), any(LocalDateTime.class));
    }
}
