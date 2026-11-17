package com.norday.core;

import com.norday.core.model.Usuario;
import com.norday.core.model.UsuarioDispositivo;
import com.norday.core.repository.IUsuarioDispositivoDAO;
import com.norday.core.service.ExportadorDispositivos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExportadorDispositivosTest {

    @Mock
    private IUsuarioDispositivoDAO dispositivoDAO;

    @InjectMocks
    private ExportadorDispositivos exportador;

    @Test
    @SuppressWarnings("unchecked")
    void exportaLasAppsYSuFecha_nuncaElToken() {
        Usuario usuario = new Usuario("Ana", "ana", "ana@norday.local", "secreto");
        usuario.setUsuarioId(1);
        when(dispositivoDAO.findByUsuario(1)).thenReturn(List.of(
                new UsuarioDispositivo(usuario, "enfoque", "token-enfoque", LocalDateTime.of(2026, 11, 15, 9, 0)),
                new UsuarioDispositivo(usuario, "habitos", "token-habitos", LocalDateTime.of(2026, 11, 1, 8, 30))));

        Map<String, Object> datos = exportador.exportar(usuario);
        List<Map<String, Object>> apps = (List<Map<String, Object>>) datos.get("apps");

        assertEquals("notificaciones", exportador.seccion());
        assertEquals(2, apps.size());
        assertEquals("enfoque", apps.get(0).get("app"));
        assertEquals("2026-11-15T09:00", apps.get(0).get("actualizadoEn"));
        assertEquals("habitos", apps.get(1).get("app"));
        for (Map<String, Object> app : apps) {
            assertFalse(app.containsKey("fcmToken"));
            assertFalse(app.containsValue("token-enfoque"));
            assertFalse(app.containsValue("token-habitos"));
        }
    }
}
