package com.norday.habitos;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.service.ExportadorHabitos;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;


@ExtendWith(MockitoExtension.class)
class ExportadorHabitosTest {

    @Mock
    private IHabitoDAO habitoDAO;

    @Mock
    private IRegistroDAO registroDAO;

    @Mock
    private IRachaDAO rachaDAO;

    @Mock
    private ICategoriaDAO categoriaDAO;

    @InjectMocks
    private ExportadorHabitos exportador;

    @Test
    @SuppressWarnings("unchecked")
    void exportar_incluyeFechaCreacionDelHabitoYCreadoEnDelRegistro() {
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(1);

        Habito habito = new Habito("Leer", null, Frecuencia.DIARIO, 1,
                usuario, null, LocalDate.of(2026, 9, 1));
        // La entidad no tiene setter de fechaCreacion a propósito.
        ReflectionTestUtils.setField(habito, "fechaCreacion",
                LocalDateTime.of(2026, 9, 1, 7, 45, 0));

        Registro registro = new Registro(habito, true, null, LocalDate.of(2026, 9, 2));
        ReflectionTestUtils.setField(registro, "creadoEn",
                LocalDateTime.of(2026, 9, 2, 21, 5, 0));

        when(habitoDAO.findByPropietario(usuario)).thenReturn(List.of(habito));
        when(rachaDAO.findByHabito(habito)).thenReturn(null);
        when(registroDAO.findByHabito(habito)).thenReturn(List.of(registro));
        when(categoriaDAO.findByCreador(usuario)).thenReturn(List.of());

        Map<String, Object> datos = exportador.exportar(usuario);

        List<Map<String, Object>> habitos = (List<Map<String, Object>>) datos.get("habitos");
        assertEquals(1, habitos.size());
        assertEquals("2026-09-01T07:45", habitos.get(0).get("fechaCreacion"));

        List<Map<String, Object>> registros =
                (List<Map<String, Object>>) habitos.get(0).get("registros");
        assertEquals(1, registros.size());
        assertEquals("2026-09-02T21:05", registros.get(0).get("creadoEn"));
    }
}
