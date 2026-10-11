package com.norday.habitos;

import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los seis logros de ritmo de la sección 23 de las decisiones del rediseño.
 * Los niveles son los de la decisión D5: "Buen ritmo" desde 0,60, "Gran
 * ritmo" desde 0,80 e "Imparable" desde 0,95; "Retomando" por debajo de 0,40.
 * Lo esperado sale de esas dos tablas, no de CalculoRitmo.
 */
class LogrosDeRitmoTest {

    /** Un ritmo que no se ha movido en la semana. */
    private static RitmoLeido quieto(double valor) {
        return new RitmoLeido(valor, valor);
    }

    private static Optional<RitmoLeido> general(double valor) {
        return Optional.of(quieto(valor));
    }

    @Test
    void sinHabitosOSinLlegarABuenRitmo_noSeCumpleNinguno() {
        assertEquals(List.of(), CalculoRitmo.logrosDeRitmo(List.of(), Optional.empty()));
        assertEquals(List.of(),
                CalculoRitmo.logrosDeRitmo(List.of(quieto(0.50), quieto(0.59)), general(0.55)));
    }

    @Test
    void unHabitoEnBuenRitmo_cumpleElPrimero() {
        assertEquals(List.of("RITMO_BUENO"),
                CalculoRitmo.logrosDeRitmo(List.of(quieto(0.60)), general(0.60)));
    }

    @Test
    void unHabitoEnGranRitmoOEnImparable_cumpleTambienLosAnteriores() {
        assertEquals(List.of("RITMO_BUENO", "RITMO_FUERTE"),
                CalculoRitmo.logrosDeRitmo(List.of(quieto(0.80)), general(0.50)));
        assertEquals(List.of("RITMO_BUENO", "RITMO_FUERTE", "RITMO_IMPARABLE"),
                CalculoRitmo.logrosDeRitmo(List.of(quieto(0.95)), general(0.50)));
    }

    @Test
    void elRitmoGeneralEnGranRitmo_cumpleElSuyo() {
        List<RitmoLeido> ritmos = List.of(quieto(0.95), quieto(0.65));

        assertEquals(List.of("RITMO_BUENO", "RITMO_FUERTE", "RITMO_IMPARABLE", "RITMO_GENERAL_FUERTE"),
                CalculoRitmo.logrosDeRitmo(ritmos, general(0.80)));
        assertEquals(List.of("RITMO_BUENO", "RITMO_FUERTE", "RITMO_IMPARABLE"),
                CalculoRitmo.logrosDeRitmo(ritmos, general(0.79)));
    }

    @Test
    void tresHabitosTodosConBuenRitmo_cumplenTodosAUna() {
        assertEquals(List.of("RITMO_BUENO", "RITMO_FUERTE", "RITMO_TODOS"),
                CalculoRitmo.logrosDeRitmo(
                        List.of(quieto(0.60), quieto(0.70), quieto(0.90)), general(0.73)));
    }

    @Test
    void conUnoFlojoOConSoloDosHabitos_noSeCumpleTodosAUna() {
        assertEquals(List.of("RITMO_BUENO", "RITMO_FUERTE"),
                CalculoRitmo.logrosDeRitmo(
                        List.of(quieto(0.59), quieto(0.70), quieto(0.90)), general(0.73)));
        assertEquals(List.of("RITMO_BUENO"),
                CalculoRitmo.logrosDeRitmo(List.of(quieto(0.60), quieto(0.70)), general(0.65)));
    }

    @Test
    void deVuelta_esSubirDeRetomandoABuenRitmoEnUnaSemana() {
        // Hace siete días en 0,30 ("Retomando") y hoy en 0,62 ("Buen ritmo").
        assertEquals(List.of("RITMO_BUENO", "RITMO_VUELTA"),
                CalculoRitmo.logrosDeRitmo(List.of(new RitmoLeido(0.62, 0.30)), general(0.62)));
        // Hace siete días en 0,45: no venía de "Retomando".
        assertEquals(List.of("RITMO_BUENO"),
                CalculoRitmo.logrosDeRitmo(List.of(new RitmoLeido(0.62, 0.45)), general(0.62)));
        // Venía de "Retomando" pero aún no ha llegado a "Buen ritmo".
        assertEquals(List.of(),
                CalculoRitmo.logrosDeRitmo(List.of(new RitmoLeido(0.50, 0.30)), general(0.50)));
    }
}
