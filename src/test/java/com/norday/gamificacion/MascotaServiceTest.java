package com.norday.gamificacion;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.MascotaDTO;
import com.norday.gamificacion.repository.IMascotaDAO;
import com.norday.gamificacion.service.MascotaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.ZoneId;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * La fase viaja como código, no como texto: el cliente la traduce, igual que
 * hace con categorías, logros y productos. El servidor no elige idioma.
 */
@ExtendWith(MockitoExtension.class)
class MascotaServiceTest {

    @Mock
    private IMascotaDAO mascotaDAO;

    @Mock
    private IUsuarioDAO usuarioDAO;

    @Mock
    private ZonaUsuarioService zonaUsuarioService;

    @InjectMocks
    private MascotaService mascotaService;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        usuario = new Usuario();
        usuario.setUsuarioId(1);
        lenient().when(zonaUsuarioService.zonaDe(any(Usuario.class)))
                .thenReturn(ZoneId.of("Europe/Madrid"));
    }

    private MascotaDTO dtoConXp(int xp) {
        Mascota mascota = new Mascota(usuario);
        mascota.setExperiencia(xp);
        when(mascotaDAO.findByUsuarioId(1)).thenReturn(mascota);
        return mascotaService.obtenerDTO(1);
    }

    @Test
    void laMascotaNaceSinNombre_paraQueNoNazcaEnUnIdiomaConcreto() {
        assertNull(new Mascota(usuario).getNombre());
    }

    @Test
    void laFaseViajaComoCodigoEnMayusculas_noComoTextoTraducible() {
        assertEquals("HUEVO", dtoConXp(0).getFase());
    }

    @Test
    void lasTresFasesDevuelvenSuCodigo() {
        assertEquals("HUEVO", dtoConXp(0).getFase());     // nivel 1
        assertEquals("CRIA", dtoConXp(100).getFase());    // nivel intermedio
        assertEquals("ADULTO", dtoConXp(100000).getFase()); // nivel alto
    }
}
