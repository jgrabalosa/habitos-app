package com.norday.core;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ExportacionDatosService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ExportacionDatosServiceTest {

    private static final List<String> CLAVES_DE_MEDICION = List.of(
            "utmSource", "utmMedium", "utmCampaign",
            "referrerCrudo", "referrerFecha", "ultimoAcceso");

    @Mock
    private IUsuarioDAO usuarioDAO;

    @InjectMocks
    private ExportacionDatosService servicio;

    private Usuario usuario;

    @BeforeEach
    void setUp() {
        // @InjectMocks no sabe rellenar un List<ExportadorDatosUsuario>: se
        // fija a mano, vacía, porque aquí solo interesa la sección «cuenta».
        ReflectionTestUtils.setField(servicio, "exportadores", List.of());
        usuario = new Usuario("Ana", "ana", "ana@norday.local", "secreto");
        usuario.setUsuarioId(1);
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> cuenta() {
        return (Map<String, Object>) servicio.exportar(1).get("cuenta");
    }

    @Test
    void usuarioConOrigenYUltimoAcceso_loExportaConSusValores() {
        usuario.setUtmSource("instagram");
        usuario.setUtmMedium("social");
        usuario.setUtmCampaign("pildora");
        usuario.setReferrerCrudo("utm_source=instagram&utm_medium=social&utm_campaign=pildora");
        usuario.setReferrerFecha(LocalDateTime.of(2026, 9, 1, 10, 30, 0));
        usuario.setUltimoAcceso(LocalDateTime.of(2026, 9, 30, 8, 15, 0));
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        Map<String, Object> cuenta = cuenta();

        assertEquals("instagram", cuenta.get("utmSource"));
        assertEquals("social", cuenta.get("utmMedium"));
        assertEquals("pildora", cuenta.get("utmCampaign"));
        assertEquals("utm_source=instagram&utm_medium=social&utm_campaign=pildora",
                cuenta.get("referrerCrudo"));
        assertEquals("2026-09-01T10:30", cuenta.get("referrerFecha"));
        assertEquals("2026-09-30T08:15", cuenta.get("ultimoAcceso"));
    }

    @Test
    void usuarioSinOrigenNiUltimoAcceso_lasSeisClavesSalenConValorNulo() {
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        Map<String, Object> cuenta = cuenta();

        for (String clave : CLAVES_DE_MEDICION) {
            assertTrue(cuenta.containsKey(clave), "Falta la clave " + clave);
            assertNull(cuenta.get(clave), "Debería ser null: " + clave);
        }
    }

    @Test
    void cuenta_nuncaIncluyeContrasenaNiFcmToken() {
        usuario.setFcmToken("token-de-dispositivo");
        when(usuarioDAO.findById(1)).thenReturn(usuario);

        Map<String, Object> cuenta = cuenta();

        assertFalse(cuenta.containsKey("contrasena"));
        assertFalse(cuenta.containsKey("fcmToken"));
    }

    @Test
    void usuarioInexistente_lanzaRecursoNoEncontrado() {
        when(usuarioDAO.findById(99)).thenReturn(null);

        assertThrows(RecursoNoEncontradoException.class, () -> servicio.exportar(99));
    }
}
