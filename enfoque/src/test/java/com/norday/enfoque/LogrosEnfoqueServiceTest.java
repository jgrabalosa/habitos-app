package com.norday.enfoque;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.enfoque.service.LogrosEnfoqueService;
import com.norday.gamificacion.service.LogroService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Cuándo da cada logro de Norday Focus. El DAO está simulado: aquí se prueban
 * los umbrales y las reglas; las consultas, en SesionEnfoqueTest con Postgres.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class LogrosEnfoqueServiceTest {

    private static final ZoneId MADRID = ZoneId.of("Europe/Madrid");
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Mock private ISesionEnfoqueDAO sesionDAO;
    @Mock private LogroService logroService;
    @Mock private ZonaUsuarioService zonaUsuarioService;

    private LogrosEnfoqueService servicio;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        servicio = new LogrosEnfoqueService();
        ReflectionTestUtils.setField(servicio, "sesionDAO", sesionDAO);
        ReflectionTestUtils.setField(servicio, "logroService", logroService);
        ReflectionTestUtils.setField(servicio, "zonaUsuarioService", zonaUsuarioService);
        usuario = new Usuario();
        usuario.setUsuarioId(7);
        when(zonaUsuarioService.zonaDe(usuario)).thenReturn(MADRID);
        // Lo mínimo: la sesión nueva, sola.
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(25);
        when(sesionDAO.completadasDelDia(7, VIERNES)).thenReturn(1);
        when(sesionDAO.ultimosEstados(eq(7), anyInt())).thenReturn(List.of(EstadoSesion.COMPLETADA));
        when(sesionDAO.diasConSesion(7)).thenReturn(1);
        when(sesionDAO.diasQueCuentanHasta(7, VIERNES)).thenReturn(List.of(VIERNES));
        when(sesionDAO.minutosTotales(7)).thenReturn(25L);
    }

    /** Una sesión que empieza a las {@code hora}:{@code minuto} de Madrid el viernes (UTC+2 en octubre). */
    private static SesionEnfoque sesion(EstadoSesion estado, int objetivo, int hora, int minuto) {
        SesionEnfoque s = new SesionEnfoque();
        s.setId(41);
        s.setEstado(estado);
        s.setDuracionObjetivoMin(objetivo);
        s.setDuracionSeg(objetivo * 60);
        s.setDia(VIERNES);
        s.setInicioUtc(LocalDateTime.of(2026, 10, 9, hora, minuto).minusHours(2));
        return s;
    }

    private static SesionEnfoque normal() {
        return sesion(EstadoSesion.COMPLETADA, 25, 10, 0);
    }

    /** Los logros que concede una evaluación (solo esa: se olvidan las anteriores del mismo test). */
    private Set<String> dados(SesionEnfoque sesion, boolean tope) {
        clearInvocations(logroService);
        servicio.evaluar(usuario, sesion, tope);
        ArgumentCaptor<String> codigos = ArgumentCaptor.forClass(String.class);
        verify(logroService, atLeast(0)).otorgarSiNoTiene(eq(usuario), codigos.capture());
        return new TreeSet<>(codigos.getAllValues());
    }

    private Set<String> dados(SesionEnfoque sesion) {
        return dados(sesion, false);
    }

    @Test
    void laPrimeraSesion_daSoloElPrimero() {
        assertEquals(Set.of("ENFOQUE_PRIMERA_SESION"), dados(normal()));
    }

    @Test
    void unaPerdida_noDaNinguno() {
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(500);
        when(sesionDAO.diasConSesion(7)).thenReturn(100);
        assertTrue(dados(sesion(EstadoSesion.PERDIDA, 25, 6, 0), true).isEmpty());
        verifyNoInteractions(logroService);
    }

    @Test
    void unaTerminada_cuenta() {
        assertTrue(dados(sesion(EstadoSesion.TERMINADA, 25, 10, 0)).contains("ENFOQUE_PRIMERA_SESION"));
    }

    @Test
    void conDestino() {
        SesionEnfoque s = normal();
        s.setDestinoTipo("habito");
        s.setDestinoRef(3L);
        assertTrue(dados(s).contains("ENFOQUE_CON_DESTINO"));
        assertFalse(dados(normal()).contains("ENFOQUE_CON_DESTINO"));
    }

    @Test
    void diaDeEnfoque_desde50Minutos() {
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(49);
        assertFalse(dados(normal()).contains("ENFOQUE_DIA"));
        when(sesionDAO.minutosDelDia(7, VIERNES)).thenReturn(50);
        assertTrue(dados(normal()).contains("ENFOQUE_DIA"));
    }

    @Test
    void tope_soloSiSeAlcanza() {
        assertFalse(dados(normal(), false).contains("ENFOQUE_TOPE"));
        assertTrue(dados(normal(), true).contains("ENFOQUE_TOPE"));
    }

    @Test
    void sesionesLargas_soloCompletadas() {
        assertTrue(dados(sesion(EstadoSesion.COMPLETADA, 60, 10, 0)).contains("ENFOQUE_SESION_60"));
        assertFalse(dados(sesion(EstadoSesion.COMPLETADA, 60, 10, 0)).contains("ENFOQUE_SESION_120"));
        assertFalse(dados(sesion(EstadoSesion.COMPLETADA, 55, 10, 0)).contains("ENFOQUE_SESION_60"));
        assertFalse(dados(sesion(EstadoSesion.TERMINADA, 120, 10, 0)).contains("ENFOQUE_SESION_60"),
                "terminada antes de tiempo no es completar");
        Set<String> maraton = dados(sesion(EstadoSesion.COMPLETADA, 120, 10, 0));
        assertTrue(maraton.contains("ENFOQUE_SESION_60") && maraton.contains("ENFOQUE_SESION_120"));
    }

    @Test
    void cuatroCompletadasEnUnDia() {
        when(sesionDAO.completadasDelDia(7, VIERNES)).thenReturn(3);
        assertFalse(dados(normal()).contains("ENFOQUE_CUATRO"));
        when(sesionDAO.completadasDelDia(7, VIERNES)).thenReturn(4);
        assertTrue(dados(normal()).contains("ENFOQUE_CUATRO"));
        assertFalse(dados(sesion(EstadoSesion.TERMINADA, 25, 10, 0)).contains("ENFOQUE_CUATRO"),
                "lo da una completada");
    }

    @Test
    void sinRendirse_diezSeguidasSinPerder() {
        List<EstadoSesion> diez = new ArrayList<>(Collections.nCopies(10, EstadoSesion.COMPLETADA));
        diez.set(4, EstadoSesion.TERMINADA);
        when(sesionDAO.ultimosEstados(7, 10)).thenReturn(diez);
        assertTrue(dados(normal()).contains("ENFOQUE_SIN_RENDIRSE"));

        diez.set(9, EstadoSesion.PERDIDA);
        assertFalse(dados(normal()).contains("ENFOQUE_SIN_RENDIRSE"));

        when(sesionDAO.ultimosEstados(7, 10)).thenReturn(Collections.nCopies(9, EstadoSesion.COMPLETADA));
        assertFalse(dados(normal()).contains("ENFOQUE_SIN_RENDIRSE"), "solo nueve");
    }

    @Test
    void horario_enLaZonaDelUsuario() {
        assertTrue(dados(sesion(EstadoSesion.COMPLETADA, 25, 7, 59)).contains("ENFOQUE_MANANA"));
        assertFalse(dados(sesion(EstadoSesion.COMPLETADA, 25, 8, 0)).contains("ENFOQUE_MANANA"));
        assertTrue(dados(sesion(EstadoSesion.COMPLETADA, 25, 22, 0)).contains("ENFOQUE_NOCHE"));
        assertFalse(dados(sesion(EstadoSesion.COMPLETADA, 25, 21, 59)).contains("ENFOQUE_NOCHE"));
    }

    @Test
    void diasDistintos_porUmbrales() {
        when(sesionDAO.diasConSesion(7)).thenReturn(7);
        Set<String> d = dados(normal());
        assertTrue(d.contains("ENFOQUE_DIAS_3") && d.contains("ENFOQUE_DIAS_7"));
        assertFalse(d.contains("ENFOQUE_DIAS_30"));
    }

    @Test
    void racha_diasSeguidosHastaElDiaDeLaSesion() {
        when(sesionDAO.diasQueCuentanHasta(7, VIERNES)).thenReturn(List.of(
                VIERNES, VIERNES.minusDays(1), VIERNES.minusDays(2), VIERNES.minusDays(4)));
        Set<String> d = dados(normal());
        assertTrue(d.contains("ENFOQUE_RACHA_3"));
        assertFalse(d.contains("ENFOQUE_RACHA_7"));

        when(sesionDAO.diasQueCuentanHasta(7, VIERNES)).thenReturn(List.of(
                VIERNES, VIERNES.minusDays(2), VIERNES.minusDays(3)));
        assertFalse(dados(normal()).contains("ENFOQUE_RACHA_3"), "el hueco del jueves la corta");
    }

    @Test
    void horas_enteras_delTotal() {
        when(sesionDAO.minutosTotales(7)).thenReturn(299L);
        assertFalse(dados(normal()).contains("ENFOQUE_HORAS_5"));
        when(sesionDAO.minutosTotales(7)).thenReturn(300L);
        assertTrue(dados(normal()).contains("ENFOQUE_HORAS_5"));
        when(sesionDAO.minutosTotales(7)).thenReturn(30000L);
        Set<String> d = dados(normal());
        assertTrue(d.containsAll(Set.of("ENFOQUE_HORAS_5", "ENFOQUE_HORAS_25", "ENFOQUE_HORAS_100",
                "ENFOQUE_HORAS_500")));
    }
}
