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

import java.time.LocalDate;
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

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

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
        lenient().when(zonaUsuarioService.zonaDe(any(Usuario.class))).thenReturn(ZONA);
    }

    private MascotaDTO dtoConXp(int xp) {
        Mascota mascota = new Mascota(usuario);
        mascota.setExperiencia(xp);
        when(mascotaDAO.findByUsuarioId(1)).thenReturn(mascota);
        return mascotaService.obtenerDTO(1);
    }

    /** diasAtras a null = nunca hubo un día completo. */
    private MascotaDTO dtoConUltimoDiaCompleto(Integer diasAtras) {
        Mascota mascota = new Mascota(usuario);
        if (diasAtras != null) {
            mascota.setFechaUltimoDiaCompleto(LocalDate.now(ZONA).minusDays(diasAtras));
        }
        when(mascotaDAO.findByUsuarioId(1)).thenReturn(mascota);
        return mascotaService.obtenerDTO(1);
    }

    @Test
    void laMascotaNaceSinNombre_paraQueNoNazcaEnUnIdiomaConcreto() {
        assertNull(new Mascota(usuario).getNombre());
    }

    @Test
    void laMascotaNaceSinNingunDiaCompleto() {
        assertNull(new Mascota(usuario).getFechaUltimoDiaCompleto());
    }

    @Test
    void laFaseViajaComoCodigoEnMayusculas_noComoTextoTraducible() {
        assertEquals("HUEVO", dtoConXp(0).getFase());
    }

    @Test
    void lasTresFasesDevuelvenSuCodigo() {
        assertEquals("HUEVO", dtoConXp(0).getFase());       // nivel 1
        assertEquals("CRIA", dtoConXp(100).getFase());      // nivel intermedio
        assertEquals("ADULTO", dtoConXp(100000).getFase()); // nivel alto
    }

    // ── Curva de XP: costoNivel(n) = 15×(n-1) ─────────────────────────────
    // Acumulado para llegar a cada nivel: 1→0, 2→15, 3→45, 4→90, 5→150

    @Test
    void subirAlNivelNCuesta15PorNMenosUno() {
        // Recién nacida: para el nivel 2 faltan 15
        assertEquals(1, dtoConXp(0).getNivel());
        assertEquals(15, dtoConXp(0).getXpParaSiguienteNivel());

        // Ya en nivel 2: el 3 cuesta 30
        assertEquals(2, dtoConXp(15).getNivel());
        assertEquals(30, dtoConXp(15).getXpParaSiguienteNivel());
    }

    @Test
    void elNivelSubeJustoAlAlcanzarElAcumulado_niAntesNiDespues() {
        assertEquals(1, dtoConXp(14).getNivel());
        assertEquals(2, dtoConXp(15).getNivel());
        assertEquals(2, dtoConXp(44).getNivel());
        assertEquals(3, dtoConXp(45).getNivel());
        assertEquals(4, dtoConXp(90).getNivel());
        assertEquals(5, dtoConXp(150).getNivel());
    }

    @Test
    void laXpDentroDelNivelSeMideDesdeElInicioDeEseNivel() {
        MascotaDTO dto = dtoConXp(60); // el nivel 3 empieza en 45
        assertEquals(3, dto.getNivel());
        assertEquals(15, dto.getXpEnNivelActual());
    }

    // ── Estado de ánimo: sale de fechaUltimoDiaCompleto ───────────────────

    @Test
    void sinNingunDiaCompletoLaMascotaEstaTriste() {
        assertEquals("triste", dtoConUltimoDiaCompleto(null).getEstado());
    }

    @Test
    void elMismoDiaEnQueSeCumpleTodoEstaFeliz() {
        assertEquals("feliz", dtoConUltimoDiaCompleto(0).getEstado());
    }

    @Test
    void unoODosDiasSinCumplirLaDejanDormida() {
        assertEquals("dormida", dtoConUltimoDiaCompleto(1).getEstado());
        assertEquals("dormida", dtoConUltimoDiaCompleto(2).getEstado());
    }

    @Test
    void alTercerDiaSinCumplirVuelveAEstarTriste() {
        assertEquals("triste", dtoConUltimoDiaCompleto(3).getEstado());
        assertEquals("triste", dtoConUltimoDiaCompleto(30).getEstado());
    }

    // ── registrarDiaCompleto ─────────────────────────────────────────────

    @Test
    void registrarDiaCompletoSellaHoy() {
        Mascota mascota = new Mascota(usuario);
        when(mascotaDAO.findByUsuarioId(1)).thenReturn(mascota);

        mascotaService.registrarDiaCompleto(1);

        assertEquals(LocalDate.now(ZONA), mascota.getFechaUltimoDiaCompleto());
        verify(mascotaDAO).update(mascota);
    }

    @Test
    void registrarDiaCompletoDosVecesElMismoDiaNoVuelveAEscribir() {
        Mascota mascota = new Mascota(usuario);
        mascota.setFechaUltimoDiaCompleto(LocalDate.now(ZONA));
        when(mascotaDAO.findByUsuarioId(1)).thenReturn(mascota);

        mascotaService.registrarDiaCompleto(1);

        verify(mascotaDAO, never()).update(any());
    }

    @Test
    void registrarDiaCompletoPisaElSelloDeUnDiaAnterior() {
        Mascota mascota = new Mascota(usuario);
        mascota.setFechaUltimoDiaCompleto(LocalDate.now(ZONA).minusDays(4));
        when(mascotaDAO.findByUsuarioId(1)).thenReturn(mascota);

        mascotaService.registrarDiaCompleto(1);

        assertEquals(LocalDate.now(ZONA), mascota.getFechaUltimoDiaCompleto());
        verify(mascotaDAO).update(mascota);
    }
}
