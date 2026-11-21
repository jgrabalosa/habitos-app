package com.norday;

import com.norday.core.model.AppNorday;
import com.norday.core.model.Usuario;
import com.norday.core.service.DispositivoService;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.AvisoApp;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.TipoAviso;
import com.norday.habitos.repository.IAvisoAppDAO;
import com.norday.habitos.service.AvisosAppScheduler;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * El barrido de los avisos de la app contra Postgres de verdad: las consultas
 * nuevas (último completado de cada usuario, avisos de varios usuarios, registros
 * de las 48 h) y que un aviso se guarda con el usuario que el barrido leyó en otra
 * transacción. Los tests unitarios mockean los DAOs y no validan nada de eso.
 *
 * Sin @Transactional a propósito: en producción el barrido no es transaccional y
 * cada DAO abre la suya, así que el usuario llega desconectado. Los datos se
 * confirman de verdad y cada test los borra con la baja de la cuenta. Solo el
 * envío a Firebase es falso, cambiado a mano como en TokenPorAppTest.
 */
@SpringBootTest
class AvisosAppBarridoTest {

    /** Viernes 20-nov-2026, las 19:00 en Madrid. */
    private static final Instant VIERNES_19 = Instant.parse("2026-11-20T18:00:00Z");
    /** Viernes 20-nov-2026, las 18:00 en Madrid. */
    private static final Instant VIERNES_18 = Instant.parse("2026-11-20T17:00:00Z");

    @Autowired
    private AvisosAppScheduler scheduler;
    @Autowired
    private DispositivoService dispositivoService;
    @Autowired
    private UsuarioService usuarioService;
    @Autowired
    private IAvisoAppDAO avisoAppDAO;
    @Autowired
    private PlatformTransactionManager transacciones;
    @PersistenceContext
    private EntityManager em;

    private NotificacionService envio;
    private NotificacionService envioReal;
    private final List<Integer> creados = new ArrayList<>();

    @BeforeEach
    void envioFalso() {
        envioReal = (NotificacionService) ReflectionTestUtils.getField(scheduler, "notificacionService");
        envio = mock(NotificacionService.class);
        ReflectionTestUtils.setField(scheduler, "notificacionService", envio);
    }

    @AfterEach
    void envioRealYBorrado() {
        ReflectionTestUtils.setField(scheduler, "notificacionService", envioReal);
        for (int id : creados) {
            usuarioService.eliminarCuenta(id);
        }
    }

    private <T> T enTransaccion(Supplier<T> accion) {
        return new TransactionTemplate(transacciones).execute(status -> accion.get());
    }

    /** Un usuario de Madrid con token de Habits, y su id. */
    private int usuario() {
        int id = enTransaccion(() -> {
            Usuario usuario = new Usuario();
            usuario.setNombre("Test avisos");
            usuario.setUsername("test-avisos-" + System.nanoTime());
            usuario.setEmail("test-avisos-" + System.nanoTime() + "@example.com");
            usuario.setContrasena("contrasenaDePrueba");
            usuario.setProveedorAuth("LOCAL");
            usuario.setFechaRegistro(LocalDateTime.now());
            usuario.setIdioma("es");
            usuario.setZonaHoraria("Europe/Madrid");
            em.persist(usuario);
            em.flush();
            return usuario.getUsuarioId();
        });
        creados.add(id);
        dispositivoService.registrarToken(id, AppNorday.HABITOS, "tok-" + id);
        return id;
    }

    private Habito habito(int usuarioId, String nombre, Frecuencia frecuencia, int meta) {
        return enTransaccion(() -> {
            Habito habito = new Habito(nombre, null, frecuencia, meta, em.find(Usuario.class, usuarioId), null,
                    LocalDate.of(2026, 1, 1));
            habito.setRecordatorioActivo(false);
            em.persist(habito);
            em.flush();
            return habito;
        });
    }

    private void completado(Habito habito, LocalDate fecha) {
        enTransaccion(() -> {
            em.persist(new Registro(em.find(Habito.class, habito.getHabitoId()), true, null, fecha));
            return null;
        });
    }

    private void avisoEnviado(int usuarioId, TipoAviso tipo, Instant cuando) {
        enTransaccion(() -> {
            AvisoApp aviso = new AvisoApp(em.find(Usuario.class, usuarioId), tipo, 1);
            aviso.setEnviadoEn(LocalDateTime.ofInstant(cuando, java.time.ZoneOffset.UTC));
            return avisoAppDAO.save(aviso);
        });
    }

    @Test
    void laVuelta_seEnviaYSeApunta_yNoSeRepiteElMismoDia() {
        int id = usuario();
        completado(habito(id, "Leer", Frecuencia.DIARIO, 1), LocalDate.of(2026, 11, 16));

        scheduler.enviarAvisos(VIERNES_19);
        scheduler.enviarAvisos(VIERNES_19);

        verify(envio, times(1)).enviarNotificacion(eq("tok-" + id), anyString(), anyString());
        List<AvisoApp> avisos = avisoAppDAO.findByUsuario(id);
        assertEquals(1, avisos.size());
        assertEquals(TipoAviso.VUELTA_1, avisos.get(0).getTipo());
        assertTrue(avisos.get(0).getVariante() >= 1 && avisos.get(0).getVariante() <= 3);
    }

    @Test
    void elMomentoDebil_nombraElHabito_conElTextoDelBundle() {
        int id = usuario();
        Habito yoga = habito(id, "Yoga", Frecuencia.SEMANAL, 3);
        completado(yoga, LocalDate.of(2026, 11, 16)); // una de tres el lunes: faltan dos

        scheduler.enviarAvisos(VIERNES_18);

        ArgumentCaptor<String> titulo = ArgumentCaptor.forClass(String.class);
        verify(envio).enviarNotificacion(eq("tok-" + id), titulo.capture(), anyString());
        assertTrue(titulo.getValue().contains("Yoga"), titulo.getValue());
        assertEquals(TipoAviso.MOMENTO_DEBIL, avisoAppDAO.findByUsuario(id).get(0).getTipo());
    }

    @Test
    void laRetirada_seLeeDeLosRegistrosDeVerdad_yUnRegistroEnLas48hLaEvita() {
        int id = usuario();
        Habito leer = habito(id, "Leer", Frecuencia.DIARIO, 1);
        completado(leer, LocalDate.of(2026, 1, 5));
        // Tres avisos de enero sin que el usuario haga nada en las 48 h siguientes
        // (su registro se creó hoy, no en enero).
        avisoEnviado(id, TipoAviso.MOMENTO_DEBIL, Instant.parse("2026-01-09T17:00:00Z"));
        avisoEnviado(id, TipoAviso.MOMENTO_DEBIL, Instant.parse("2026-01-16T17:00:00Z"));
        avisoEnviado(id, TipoAviso.MOMENTO_DEBIL, Instant.parse("2026-01-23T17:00:00Z"));
        Instant lunes2Feb19 = Instant.parse("2026-02-02T18:00:00Z");

        scheduler.enviarAvisos(lunes2Feb19);

        verify(envio, never()).enviarNotificacion(eq("tok-" + id), anyString(), anyString());

        // Resulta que marcó el registro el sábado 24 de enero: dentro de las 48 h del último aviso.
        enTransaccion(() -> em.createNativeQuery("UPDATE registro SET creado_en = :cuando WHERE habito_ref = :habito")
                .setParameter("cuando", LocalDateTime.of(2026, 1, 24, 10, 0))
                .setParameter("habito", leer.getHabitoId())
                .executeUpdate());
        clearInvocations(envio);

        scheduler.enviarAvisos(lunes2Feb19);

        verify(envio).enviarNotificacion(eq("tok-" + id), anyString(), anyString());
        assertEquals(4, avisoAppDAO.findByUsuario(id).size());
    }
}
