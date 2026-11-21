package com.norday.gamificacion;

import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.gamificacion.service.CumplimientoCombinado;
import com.norday.gamificacion.service.CumplimientoDiarioPort;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Collections;
import java.util.List;

import static com.norday.gamificacion.model.AvanceDeHoy.*;
import static org.junit.jupiter.api.Assertions.*;

/**
 * El ánimo de la mascota con varias apps: solo cuentan las que el usuario usa,
 * y con una sola, su respuesta tal cual.
 */
class CumplimientoCombinadoTest {

    /** Una app de mentira: si está en uso, cómo va y si está cumplida. */
    private record App(boolean enUso, AvanceDeHoy avance) implements CumplimientoDiarioPort {
        @Override public boolean hoyCumplido(int usuarioId) { return avance == COMPLETO; }
        @Override public boolean enUso(int usuarioId) { return enUso; }
        @Override public AvanceDeHoy comoVaHoy(int usuarioId) { return avance; }
        @Override public List<AvanceDeHoy> comoVaLosProximosDias(int usuarioId, int dias) {
            return Collections.nCopies(dias, avance);
        }
    }

    private static CumplimientoCombinado con(App... apps) {
        CumplimientoCombinado combinado = new CumplimientoCombinado();
        ReflectionTestUtils.setField(combinado, "cumplimientos", List.<CumplimientoDiarioPort>of(apps));
        return combinado;
    }

    @Test
    void conUnaSolaApp_esSuRespuesta_tantoEnUsoComoSinUsar() {
        for (AvanceDeHoy avance : AvanceDeHoy.values()) {
            assertEquals(avance, con(new App(true, avance)).comoVaHoy(1));
            assertEquals(avance, con(new App(false, avance)).comoVaHoy(1));
            assertEquals(avance == COMPLETO, con(new App(true, avance)).hoyCumplido(1));
        }
    }

    @Test
    void laAppQueNoSeUsa_noCuenta() {
        // Solo Focus en uso: la app sin usar (ausente) no la pone triste.
        assertEquals(EMPEZADO, con(new App(false, AUSENTE), new App(true, EMPEZADO)).comoVaHoy(1));
        assertEquals(COMPLETO, con(new App(false, SIN_EMPEZAR), new App(true, COMPLETO)).comoVaHoy(1));
        assertTrue(con(new App(false, SIN_EMPEZAR), new App(true, COMPLETO)).hoyCumplido(1));
    }

    @Test
    void sinNingunaEnUso_cuentanTodas() {
        assertEquals(AUSENTE, con(new App(false, AUSENTE), new App(false, AUSENTE)).comoVaHoy(1));
        assertEquals(SIN_EMPEZAR, con(new App(false, SIN_EMPEZAR), new App(false, AUSENTE)).comoVaHoy(1));
        assertFalse(con(new App(false, SIN_EMPEZAR), new App(false, SIN_EMPEZAR)).hoyCumplido(1));
    }

    @Test
    void exultante_soloConTodasCompletas() {
        assertEquals(COMPLETO, con(new App(true, COMPLETO), new App(true, COMPLETO)).comoVaHoy(1));
        assertEquals(EMPEZADO, con(new App(true, COMPLETO), new App(true, SIN_EMPEZAR)).comoVaHoy(1));
        assertTrue(con(new App(true, COMPLETO), new App(true, COMPLETO)).hoyCumplido(1));
        assertFalse(con(new App(true, COMPLETO), new App(true, EMPEZADO)).hoyCumplido(1));
    }

    @Test
    void feliz_conAlgoEmpezado_triste_soloSiTodasAusentes() {
        assertEquals(EMPEZADO, con(new App(true, AUSENTE), new App(true, EMPEZADO)).comoVaHoy(1));
        assertEquals(AUSENTE, con(new App(true, AUSENTE), new App(true, AUSENTE)).comoVaHoy(1));
        assertEquals(SIN_EMPEZAR, con(new App(true, AUSENTE), new App(true, SIN_EMPEZAR)).comoVaHoy(1));
    }

    @Test
    void combinar_sinNada_esSinEmpezar() {
        assertEquals(SIN_EMPEZAR, CumplimientoCombinado.combinar(List.of()));
    }
}
