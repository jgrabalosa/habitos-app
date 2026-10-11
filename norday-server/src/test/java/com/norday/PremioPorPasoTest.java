package com.norday;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.service.RachaService;
import com.norday.habitos.service.RegistroService;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

/**
 * Cada paso cobra su parte y deshacerlo la devuelve, con la aplicación
 * ensamblada y el libro de puntos de verdad.
 *
 * Semana de referencia: el lunes 5-oct-2026. "Hoy" es siempre el viernes 9,
 * fijado en RachaService.hoyDe, que es por donde RegistroService lee el
 * reloj. Las cifras salen de la sección 11 de las decisiones del rediseño
 * (25 puntos y 5 de XP por día de hábito, un 20 % más al completar la
 * semana), no de CalculoPremio.
 *
 * El saldo total no se compara con una cifra fija: el primer completado de
 * un usuario da además un logro con sus puntos. Por eso las cifras se miran
 * en la respuesta y en el libro por origen, y el deshacer se comprueba
 * contra el saldo que había justo antes.
 */
@SpringBootTest
@Transactional
class PremioPorPasoTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);

    @Autowired
    private RegistroService registroService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

    @MockitoSpyBean
    private RachaService rachaService;

    @PersistenceContext
    private EntityManager em;

    private int usuarioId;
    private int habitoId;

    /** Un hábito creado hace meses, con su racha y su ritmo. */
    private void unHabito(Frecuencia frecuencia, int meta) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Test Premio");
        usuario.setUsername("test-premio-" + System.nanoTime());
        usuario.setEmail("test-premio-" + System.nanoTime() + "@example.com");
        usuario.setContrasena("contrasenaDePrueba");
        usuario.setProveedorAuth("LOCAL");
        usuario.setFechaRegistro(LocalDateTime.now());
        usuario.setIdioma("es");
        usuario.setZonaHoraria("Europe/Madrid");
        em.persist(usuario);

        Habito habito = new Habito("Hábito de premio", "Descripción", frecuencia, meta,
                usuario, null, LocalDate.of(2026, 1, 1));
        em.persist(habito);
        em.persist(new Racha(habito, LUNES));
        em.persist(new Ritmo(habito, LUNES));
        em.flush();
        usuarioId = usuario.getUsuarioId();
        habitoId = habito.getHabitoId();

        doReturn(VIERNES).when(rachaService).hoyDe(any(Habito.class));
    }

    /**
     * El hábito recién leído, con el contexto de persistencia vacío: cada
     * llamada a un servicio hace de petición.
     */
    private Habito habito() {
        em.flush();
        em.clear();
        return em.find(Habito.class, habitoId);
    }

    private int saldo() {
        em.flush();
        return usuarioMonedaService.consultarSaldo(usuarioId);
    }

    private int xp() {
        em.flush();
        em.clear();
        return mascotaService.obtenerOCrear(usuarioId).getExperiencia();
    }

    /** Lo que suman en el libro los movimientos de un origen. */
    private int puntosDelLibro(String origen) {
        em.flush();
        Long suma = em.createQuery(
                        "SELECT COALESCE(SUM(m.cantidad), 0) FROM UsuarioMoneda m "
                                + "WHERE m.usuario.usuarioId = :usuarioId AND m.origen = :origen",
                        Long.class)
                .setParameter("usuarioId", usuarioId)
                .setParameter("origen", origen)
                .getSingleResult();
        return suma.intValue();
    }

    @Test
    void semanal_laSemanaDelGimnasioDaCientoCincuentaYTreinta_yDeshacerLaQuintaDevuelveSuParte() {
        unHabito(Frecuencia.SEMANAL, 5);
        int xpAlEmpezar = xp();

        int puntos = 0;
        int xpGanada = 0;
        for (int dia = 0; dia < 4; dia++) {
            Map<String, Object> sesion =
                    registroService.completarHabito(habito(), "", LUNES.plusDays(dia));
            assertEquals(25, sesion.get("puntosPaso"), "puntos de la sesión " + (dia + 1));
            assertEquals(0, sesion.get("puntosExtraSemana"), "extra de la sesión " + (dia + 1));
            assertEquals(5, sesion.get("xpGanada"), "xp de la sesión " + (dia + 1));
            // El tercer día hecho del hábito trae además su hito, que va aparte.
            assertEquals(dia == 2 ? 50 : 0, sesion.get("puntosHito"), "hito de la sesión " + (dia + 1));
            puntos += (Integer) sesion.get("puntosPaso") + (Integer) sesion.get("puntosExtraSemana");
            xpGanada += (Integer) sesion.get("xpGanada");
        }
        int saldoAntesDeLaQuinta = saldo();
        int xpAntesDeLaQuinta = xp();

        Map<String, Object> quinta = registroService.completarHabito(habito(), "", VIERNES);
        puntos += (Integer) quinta.get("puntosPaso") + (Integer) quinta.get("puntosExtraSemana");
        xpGanada += (Integer) quinta.get("xpGanada");

        assertEquals(25, quinta.get("puntosPaso"));
        assertEquals(25, quinta.get("puntosExtraSemana"));
        assertEquals(0, quinta.get("puntosHito"));
        assertEquals(10, quinta.get("xpGanada"));
        assertEquals(150, puntos);
        assertEquals(30, xpGanada);
        assertEquals(125, puntosDelLibro("HABITO_COMPLETADO"));
        assertEquals(25, puntosDelLibro("EXTRA_SEMANA_COMPLETA"));
        assertEquals(50, puntosDelLibro("HITO_DIAS_HECHOS"));
        assertEquals(xpAlEmpezar + 30, xp());

        registroService.deshacerRegistro((Integer) quinta.get("registroId"));

        assertEquals(saldoAntesDeLaQuinta, saldo());
        assertEquals(xpAntesDeLaQuinta, xp());
        assertEquals(xpAlEmpezar + 20, xp());
    }

    @Test
    void diario_deCuatroCobraSeisSeisSeisSiete_yDeshacerLaUltimaDevuelveSuParte() {
        unHabito(Frecuencia.DIARIO, 4);
        int xpAlEmpezar = xp();

        int[] puntosEsperados = {6, 6, 6, 7};
        int[] xpEsperada = {1, 1, 1, 2};
        Map<String, Object> ultima = null;
        int saldoAntesDeLaUltima = 0;
        for (int vez = 0; vez < 4; vez++) {
            if (vez == 3) saldoAntesDeLaUltima = saldo();
            ultima = registroService.completarHabito(habito(), "", null);
            assertEquals(puntosEsperados[vez], ultima.get("puntosPaso"), "puntos de la vez " + (vez + 1));
            assertEquals(xpEsperada[vez], ultima.get("xpGanada"), "xp de la vez " + (vez + 1));
            assertEquals(0, ultima.get("puntosExtraSemana"), "un diario no lleva extra");
        }

        assertEquals(25, puntosDelLibro("HABITO_COMPLETADO"));
        assertEquals(0, puntosDelLibro("EXTRA_SEMANA_COMPLETA"));
        assertEquals(xpAlEmpezar + 5, xp());

        registroService.deshacerRegistro((Integer) ultima.get("registroId"));

        assertEquals(saldoAntesDeLaUltima, saldo());
        assertEquals(xpAlEmpezar + 3, xp());
    }

    @Test
    void semanal_pasarseDelObjetivoNoMueveNiElSaldoNiLaExperiencia() {
        // Objetivo 3 y cuatro sesiones: la cuarta es la que se pasa. Con
        // objetivo 2, la sesión de más sería el tercer día hecho del hábito
        // y traería su hito, que es otra regla.
        unHabito(Frecuencia.SEMANAL, 3);
        registroService.completarHabito(habito(), "", LUNES);
        registroService.completarHabito(habito(), "", LUNES.plusDays(1));
        registroService.completarHabito(habito(), "", LUNES.plusDays(2));
        int saldoAntes = saldo();
        int xpAntes = xp();

        Map<String, Object> cuarta =
                registroService.completarHabito(habito(), "", LUNES.plusDays(3));

        assertEquals(0, cuarta.get("puntosGanados"));
        assertEquals(0, cuarta.get("xpGanada"));
        assertEquals(saldoAntes, saldo());
        assertEquals(xpAntes, xp());
        assertEquals(75, puntosDelLibro("HABITO_COMPLETADO"));
        assertEquals(15, puntosDelLibro("EXTRA_SEMANA_COMPLETA"));
    }

    @Test
    void hito_elTercerDiaHechoSeCobraUnaVez_yNiRellenarNiRehacerLoDuplican() {
        unHabito(Frecuencia.DIARIO, 1);
        registroService.completarHabito(habito(), "", LUNES);
        registroService.completarHabito(habito(), "", LUNES.plusDays(2));
        int saldoAntesDelTercero = saldo();

        // Lunes, miércoles y jueves: el tercer día hecho, con el martes vacío.
        Map<String, Object> jueves =
                registroService.completarHabito(habito(), "", LUNES.plusDays(3));
        assertEquals(50, jueves.get("puntosHito"));
        assertEquals(75, jueves.get("puntosGanados"));
        assertEquals(50, puntosDelLibro("HITO_DIAS_HECHOS"));
        int saldoConElHito = saldo();

        // Rellenar el hueco del martes es el cuarto día hecho: no paga otro hito.
        Map<String, Object> martes =
                registroService.completarHabito(habito(), "", LUNES.plusDays(1));
        assertEquals(0, martes.get("puntosHito"));
        assertEquals(25, martes.get("puntosGanados"));
        assertEquals(50, puntosDelLibro("HITO_DIAS_HECHOS"));

        // Deshacer devuelve el hito con el resto, y rehacer lo paga una sola vez.
        registroService.deshacerRegistro((Integer) martes.get("registroId"));
        registroService.deshacerRegistro((Integer) jueves.get("registroId"));
        assertEquals(saldoAntesDelTercero, saldo());

        Map<String, Object> otraVez =
                registroService.completarHabito(habito(), "", LUNES.plusDays(3));
        assertEquals(50, otraVez.get("puntosHito"));
        assertEquals(saldoConElHito, saldo());
    }
}
