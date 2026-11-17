package com.norday.enfoque;

import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.TextosService;
import com.norday.core.service.UsuarioService;
import com.norday.enfoque.controller.EnfoqueController;
import com.norday.enfoque.service.DestinoNoDisponibleException;
import com.norday.enfoque.service.SesionEnfoqueService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.CannotCreateTransactionException;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * El contrato de errores de EnfoqueController, sin contexto de Spring: un
 * fallo técnico es 503 (la app reintenta con la misma clave) y una petición
 * mal formada es 400 con texto traducido (definitivo).
 */
class EnfoqueControllerTest {

    private final SesionEnfoqueService servicio = mock(SesionEnfoqueService.class);
    private final UsuarioService usuarioService = mock(UsuarioService.class);
    private final TextosService textosService = mock(TextosService.class);
    private MockMvc mockMvc;
    private Authentication autenticacion;

    @BeforeEach
    void setUp() {
        EnfoqueController controlador = new EnfoqueController();
        ReflectionTestUtils.setField(controlador, "sesionEnfoqueService", servicio);
        ReflectionTestUtils.setField(controlador, "usuarioService", usuarioService);
        ReflectionTestUtils.setField(controlador, "textosService", textosService);
        // Sin Accept, el modo standalone elegiría XML (jackson-dataformat-xml
        // está en el classpath); la aplicación ensamblada responde JSON.
        mockMvc = MockMvcBuilders.standaloneSetup(controlador).build();
        Usuario usuario = new Usuario();
        usuario.setUsuarioId(7);
        when(usuarioService.buscarPorId(7)).thenReturn(usuario);
        when(textosService.texto(anyString(), any(Usuario.class), any(Object[].class)))
                .thenAnswer(i -> "T:" + i.getArgument(0));
        autenticacion = new UsernamePasswordAuthenticationToken(new UsuarioAutenticado(7, "a@b.c"), null, List.of());
        // Los manejadores de error no reciben el Authentication como
        // argumento: lo leen del contexto, como en la aplicación.
        SecurityContextHolder.getContext().setAuthentication(autenticacion);
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ResultActions enviar(String cuerpo) throws Exception {
        return mockMvc.perform(post("/api/enfoque/sesiones").principal(autenticacion)
                .accept(MediaType.APPLICATION_JSON).contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    private static final String CUERPO = "{\"clave\":\"3f1c6a52-6a0e-4c43-9a59-0b4f3c7c2b11\","
            + "\"inicioUtc\":\"2026-10-09T07:25:00Z\",\"duracionSeg\":1500,\"duracionObjetivoMin\":25,"
            + "\"estado\":\"COMPLETADA\"}";

    @Test
    void unFalloDeBaseDeDatos_es503_conTextoTraducido() throws Exception {
        when(servicio.registrar(any(), any())).thenThrow(new DataAccessResourceFailureException("sin conexión"));
        enviar(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.error.temporal"));
    }

    @Test
    void unFalloDeTransaccion_es503() throws Exception {
        when(servicio.registrar(any(), any())).thenThrow(new CannotCreateTransactionException("pool agotado"));
        enviar(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.error.temporal"));
    }

    @Test
    void unDestinoQueNoHaPodidoRecibir_es503() throws Exception {
        when(servicio.registrar(any(), any()))
                .thenThrow(new DestinoNoDisponibleException(new IllegalArgumentException("inesperado")));
        enviar(CUERPO).andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.error.temporal"));
    }

    @Test
    void unCuerpoQueNoEsJson_es400_conTextoTraducido() throws Exception {
        enviar("{").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.peticion.mal_formada"));
        enviar(CUERPO.replace("1500", "\"mucho\"")).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.peticion.mal_formada"));
        verify(servicio, never()).registrar(any(), any());
    }

    @Test
    void unParametroDeTipoErroneo_es400_conTextoTraducido() throws Exception {
        mockMvc.perform(get("/api/enfoque/sesiones?pagina=abc").principal(autenticacion)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.peticion.mal_formada"));
        mockMvc.perform(get("/api/enfoque/sesiones?tamano=1e3").principal(autenticacion)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("T:enfoque.peticion.mal_formada"));
    }
}
