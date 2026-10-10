package com.norday.habitos;

import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoConPeso;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.ParametrosRitmo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Las cifras esperadas salen de cuentas hechas aparte, no de CalculoRitmo. Si
 * un test falla, no se ajusta la cifra a lo que dé el código.
 */
class RitmoGeneralTest {

    private static final double D = 0.000001;
    private static final LocalDate HOY = LocalDate.of(2026, 10, 8);

    private static RitmoConPeso asentado(double ritmo) {
        return new RitmoConPeso(ritmo, 1.0);
    }

    private static int nivelTrasDiasPerfectos(int dias) {
        double ritmo = CalculoRitmo.cerrarPeriodos(ParametrosRitmo.VALOR_INICIAL,
                Collections.nCopies(dias, 1.0), Frecuencia.DIARIO);
        return CalculoRitmo.nivel(ritmo);
    }

    @Test
    void nivel_segunLosUmbrales() {
        assertEquals(1, CalculoRitmo.nivel(0.0));
        assertEquals(1, CalculoRitmo.nivel(0.3999));
        assertEquals(2, CalculoRitmo.nivel(0.40));
        assertEquals(2, CalculoRitmo.nivel(0.5999));
        assertEquals(3, CalculoRitmo.nivel(0.60));
        assertEquals(3, CalculoRitmo.nivel(0.7999));
        assertEquals(4, CalculoRitmo.nivel(0.80));
        assertEquals(4, CalculoRitmo.nivel(0.9499));
        assertEquals(5, CalculoRitmo.nivel(0.95));
        assertEquals(5, CalculoRitmo.nivel(1.0));
    }

    @Test
    void nivel_unHabitoNuevoEmpiezaEnElDos() {
        assertEquals(2, CalculoRitmo.nivel(ParametrosRitmo.VALOR_INICIAL));
    }

    @Test
    void nivel_unDiarioPerfectoLlegaAlTresElDia3_alCuatroElDia10_yAlCincoElDia24() {
        assertEquals(2, nivelTrasDiasPerfectos(2));
        assertEquals(3, nivelTrasDiasPerfectos(3));
        assertEquals(3, nivelTrasDiasPerfectos(9));
        assertEquals(4, nivelTrasDiasPerfectos(10));
        assertEquals(4, nivelTrasDiasPerfectos(23));
        assertEquals(5, nivelTrasDiasPerfectos(24));
    }

    @Test
    void peso_creceDeCeroAUnoEnCatorceDias() {
        assertEquals(0.0, CalculoRitmo.pesoEnElGeneral(HOY, HOY), D);
        assertEquals(0.071429, CalculoRitmo.pesoEnElGeneral(HOY.minusDays(1), HOY), D);
        assertEquals(0.5, CalculoRitmo.pesoEnElGeneral(HOY.minusDays(7), HOY), D);
        assertEquals(1.0, CalculoRitmo.pesoEnElGeneral(HOY.minusDays(14), HOY), D);
        assertEquals(1.0, CalculoRitmo.pesoEnElGeneral(HOY.minusDays(300), HOY), D);
    }

    @Test
    void peso_sinFechaDeInicioCuentaEntero_yUnaFechaFuturaNoPesa() {
        assertEquals(1.0, CalculoRitmo.pesoEnElGeneral(null, HOY), D);
        assertEquals(0.0, CalculoRitmo.pesoEnElGeneral(HOY.plusDays(1), HOY), D);
    }

    @Test
    void general_esLaMediaDeLosRitmos() {
        double general = CalculoRitmo.general(
                List.of(asentado(0.9), asentado(0.5), asentado(0.7))).orElseThrow();

        assertEquals(0.7, general, D);
    }

    @Test
    void general_anadirUnHabitoNuevoNoLoBaja() {
        double antes = CalculoRitmo.general(
                List.of(asentado(0.9), asentado(0.9), asentado(0.9))).orElseThrow();
        RitmoConPeso reciencreado = new RitmoConPeso(
                ParametrosRitmo.VALOR_INICIAL, CalculoRitmo.pesoEnElGeneral(HOY, HOY));
        double despues = CalculoRitmo.general(
                List.of(asentado(0.9), asentado(0.9), asentado(0.9), reciencreado)).orElseThrow();

        assertEquals(0.9, antes, D);
        assertEquals(0.9, despues, D);
    }

    @Test
    void general_elHabitoNuevoEntraPocoAPoco() {
        // Tres hábitos asentados en 0,9 y uno nuevo que se cumple cada día.
        RitmoConPeso alDia7 = new RitmoConPeso(0.75, CalculoRitmo.pesoEnElGeneral(HOY.minusDays(7), HOY));
        RitmoConPeso alDia14 = new RitmoConPeso(0.875, CalculoRitmo.pesoEnElGeneral(HOY.minusDays(14), HOY));

        double dia7 = CalculoRitmo.general(
                List.of(asentado(0.9), asentado(0.9), asentado(0.9), alDia7)).orElseThrow();
        double dia14 = CalculoRitmo.general(
                List.of(asentado(0.9), asentado(0.9), asentado(0.9), alDia14)).orElseThrow();

        assertEquals(0.878571, dia7, D);
        assertEquals(0.893750, dia14, D);
    }

    @Test
    void general_siTodosSonRecienCreadosEsSuMediaNormal() {
        double general = CalculoRitmo.general(
                List.of(new RitmoConPeso(0.6, 0.0), new RitmoConPeso(0.4, 0.0))).orElseThrow();

        assertEquals(0.5, general, D);
    }

    @Test
    void general_sinHabitosNoHayRitmoGeneral() {
        assertTrue(CalculoRitmo.general(List.of()).isEmpty());
    }
}
