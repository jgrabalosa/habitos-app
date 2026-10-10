package com.norday.habitos;

import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoAlDia;
import com.norday.habitos.model.Frecuencia;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Semana de referencia: el lunes 5-oct-2026 al domingo 11-oct-2026. El lunes
 * siguiente es el 12.
 *
 * Las cifras esperadas salen de una simulación aparte, no de CalculoRitmo. Si
 * un test falla, no se ajusta la cifra a lo que dé el código.
 */
class RitmoAlDiaTest {

    private static final double D = 0.000001;
    /** Un hábito creado hace meses: ninguno de estos periodos es su primero. */
    private static final LocalDate ANTIGUO = LocalDate.of(2026, 1, 1);

    private static LocalDate d(int mes, int dia) {
        return LocalDate.of(2026, mes, dia);
    }

    private static void assertAlDia(double valor, double valorAncla, LocalDate desdeAncla, RitmoAlDia real) {
        assertEquals(valor, real.valor(), D, "ritmo vigente");
        assertEquals(valorAncla, real.valorAncla(), D, "valor del ancla");
        assertEquals(desdeAncla, real.desdeAncla(), "fecha del ancla");
    }

    @Test
    void diario_cierraLosDiasTerminados_yHoyNoCuenta() {
        RitmoAlDia sinHoy = CalculoRitmo.alDia(0.5, d(10, 5), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(d(10, 5), d(10, 6), d(10, 7)), d(10, 8));
        RitmoAlDia conHoy = CalculoRitmo.alDia(0.5, d(10, 5), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(d(10, 5), d(10, 6), d(10, 7), d(10, 8)), d(10, 8));

        assertAlDia(0.628501, 0.5, d(10, 5), sinHoy);
        assertAlDia(0.628501, 0.5, d(10, 5), conHoy);
    }

    @Test
    void diario_unDiaParcialCuentaEnProporcion() {
        RitmoAlDia tresDeCuatro = CalculoRitmo.alDia(0.5, d(10, 5), Frecuencia.DIARIO, 4, ANTIGUO,
                List.of(d(10, 5), d(10, 5), d(10, 5)), d(10, 6));

        assertAlDia(0.523569, 0.5, d(10, 5), tresDeCuatro);
    }

    @Test
    void diario_unDiaSinNadaBajaPoco() {
        RitmoAlDia sinNada = CalculoRitmo.alDia(0.5, d(10, 5), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(), d(10, 6));

        assertAlDia(0.483766, 0.5, d(10, 5), sinNada);
    }

    @Test
    void elPrimerDiaDeUnHabitoSoloPuedeSubir() {
        LocalDate creado = d(10, 5);

        RitmoAlDia primerDiaSinNada = CalculoRitmo.alDia(0.5, creado, Frecuencia.DIARIO, 1, creado,
                List.of(), d(10, 6));
        RitmoAlDia primerDiaHecho = CalculoRitmo.alDia(0.5, creado, Frecuencia.DIARIO, 1, creado,
                List.of(d(10, 5)), d(10, 6));
        RitmoAlDia segundoDiaSinNada = CalculoRitmo.alDia(0.5, creado, Frecuencia.DIARIO, 1, creado,
                List.of(), d(10, 7));

        assertAlDia(0.5, 0.5, d(10, 5), primerDiaSinNada);
        assertAlDia(0.547138, 0.5, d(10, 5), primerDiaHecho);
        assertAlDia(0.483766, 0.5, d(10, 5), segundoDiaSinNada);
    }

    @Test
    void diario_elAnclaAvanzaAlLunesDeEstaSemana() {
        // Ancla el jueves 1; hoy es miércoles 7. Seis días hechos: del 1 al 6.
        RitmoAlDia alDia = CalculoRitmo.alDia(0.5, d(10, 1), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(d(10, 1), d(10, 2), d(10, 3), d(10, 4), d(10, 5), d(10, 6)), d(10, 7));

        // El ancla recoge los cuatro días de la semana pasada y se queda en
        // el lunes 5; el lunes y el martes de esta semana aún pueden cambiar.
        assertAlDia(0.723978, 0.663525, d(10, 5), alDia);
    }

    @Test
    void semanal_laSemanaAbiertaNoMueveElRitmo() {
        RitmoAlDia viernes = CalculoRitmo.alDia(0.5, d(10, 5), Frecuencia.SEMANAL, 5, ANTIGUO,
                List.of(d(10, 5), d(10, 6), d(10, 7)), d(10, 9));

        assertAlDia(0.5, 0.5, d(10, 5), viernes);
    }

    @Test
    void semanal_alCerrarLaSemanaCuentaLoHecho_yElAnclaPasaAlLunes() {
        RitmoAlDia lunesSiguiente = CalculoRitmo.alDia(0.5, d(10, 5), Frecuencia.SEMANAL, 5, ANTIGUO,
                List.of(d(10, 5), d(10, 6), d(10, 7)), d(10, 12));

        assertAlDia(0.55, 0.55, d(10, 12), lunesSiguiente);
    }

    @Test
    void semanal_conElAnclaAMitadDeSemanaCuentaLaSemanaEntera() {
        // Ancla el sábado 10, pero el martes y el miércoles ya se había hecho.
        RitmoAlDia alDia = CalculoRitmo.alDia(0.5, d(10, 10), Frecuencia.SEMANAL, 3, ANTIGUO,
                List.of(d(10, 6), d(10, 7), d(10, 10)), d(10, 12));

        assertAlDia(0.75, 0.75, d(10, 12), alDia);
    }

    @Test
    void laPrimeraSemanaDeUnHabitoSoloPuedeSubir() {
        LocalDate creadoElSabado = d(10, 10);
        List<LocalDate> sabadoYDomingo = List.of(d(10, 10), d(10, 11));

        RitmoAlDia noLlega = CalculoRitmo.alDia(0.5, creadoElSabado, Frecuencia.SEMANAL, 5,
                creadoElSabado, sabadoYDomingo, d(10, 12));
        RitmoAlDia llega = CalculoRitmo.alDia(0.5, creadoElSabado, Frecuencia.SEMANAL, 2,
                creadoElSabado, sabadoYDomingo, d(10, 12));
        RitmoAlDia segundaSemanaSinNada = CalculoRitmo.alDia(0.5, creadoElSabado, Frecuencia.SEMANAL, 5,
                creadoElSabado, sabadoYDomingo, d(10, 19));

        assertAlDia(0.5, 0.5, d(10, 12), noLlega);
        assertAlDia(0.75, 0.75, d(10, 12), llega);
        assertAlDia(0.396850, 0.396850, d(10, 19), segundaSemanaSinNada);
    }

    @Test
    void semanal_variasSemanasFuera() {
        // Ancla el lunes 14-sep; cuatro semanas enteras sin nada.
        RitmoAlDia alDia = CalculoRitmo.alDia(0.5, d(9, 14), Frecuencia.SEMANAL, 3, ANTIGUO,
                List.of(), d(10, 12));

        assertAlDia(0.198425, 0.198425, d(10, 12), alDia);
    }

    @Test
    void siNoHaCerradoNada_devuelveElAnclaTalCual() {
        RitmoAlDia anclaHoy = CalculoRitmo.alDia(0.5, d(10, 8), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(), d(10, 8));
        RitmoAlDia anclaManana = CalculoRitmo.alDia(0.5, d(10, 9), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(), d(10, 8));

        assertAlDia(0.5, 0.5, d(10, 8), anclaHoy);
        assertAlDia(0.5, 0.5, d(10, 9), anclaManana);
    }

    @Test
    void subeYBajaEnLaMismaSemana() {
        // Lunes hecho, martes sin nada; hoy es miércoles.
        RitmoAlDia alDia = CalculoRitmo.alDia(0.8, d(10, 5), Frecuencia.DIARIO, 1, ANTIGUO,
                List.of(d(10, 5)), d(10, 7));

        assertAlDia(0.792268, 0.8, d(10, 5), alDia);
    }
}
