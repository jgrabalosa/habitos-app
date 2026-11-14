package com.norday.habitos;

import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Objetivo;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * La versión vigente de un objetivo en cada fecha, y los días hechos
 * juzgando cada día con la suya. Sin base de datos ni reloj.
 */
class ObjetivoVigenteTest {

    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);
    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate JUEVES = LocalDate.of(2026, 10, 8);
    private static final LocalDate LUNES_SIGUIENTE = LocalDate.of(2026, 10, 12);

    /** Un hábito cuya copia de hoy es la de la última versión: semanal de 3. */
    private static Habito habito() {
        Habito habito = new Habito();
        habito.setHabitoId(7);
        habito.setNombre("Leer");
        habito.setFrecuencia(Frecuencia.SEMANAL);
        habito.setMeta(3);
        habito.setFechaInicio(INICIO);
        habito.setActivo(true);
        return habito;
    }

    private static HabitoObjetivo version(Habito habito, LocalDate desde, Frecuencia frecuencia, int meta) {
        return new HabitoObjetivo(habito, desde, new Objetivo(frecuencia, meta, null));
    }

    /** Diario de 1 desde el inicio, de 2 desde el jueves y semanal de 3 desde el lunes siguiente. */
    private static HistorialObjetivos tresVersiones(Habito habito) {
        // Desordenadas a propósito: el historial las ordena.
        return HistorialObjetivos.de(habito, List.of(
                version(habito, LUNES_SIGUIENTE, Frecuencia.SEMANAL, 3),
                version(habito, INICIO, Frecuencia.DIARIO, 1),
                version(habito, JUEVES, Frecuencia.DIARIO, 2)));
    }

    @Test
    void laVigenteEsLaDeMayorDesdeQueNoPasaLaFecha() {
        HistorialObjetivos historial = tresVersiones(habito());

        assertEquals(new Objetivo(Frecuencia.DIARIO, 1, null), historial.objetivoEl(LUNES));
        assertEquals(new Objetivo(Frecuencia.DIARIO, 1, null), historial.objetivoEl(JUEVES.minusDays(1)));
        assertEquals(new Objetivo(Frecuencia.DIARIO, 2, null), historial.objetivoEl(JUEVES));
        assertEquals(new Objetivo(Frecuencia.DIARIO, 2, null), historial.objetivoEl(LUNES_SIGUIENTE.minusDays(1)));
        assertEquals(new Objetivo(Frecuencia.SEMANAL, 3, null), historial.objetivoEl(LUNES_SIGUIENTE));
        assertEquals(new Objetivo(Frecuencia.SEMANAL, 3, null), historial.objetivoEl(LUNES_SIGUIENTE.plusYears(1)));
    }

    @Test
    void unaFechaAnteriorALaPrimeraVersion_seJuzgaConLaPrimera() {
        HistorialObjetivos historial = tresVersiones(habito());

        assertEquals(new Objetivo(Frecuencia.DIARIO, 1, null), historial.objetivoEl(INICIO.minusDays(10)));
    }

    @Test
    void sinVersiones_seJuzgaConLaCopiaDelHabito() {
        Habito habito = habito();
        HistorialObjetivos historial = HistorialObjetivos.sinVersiones(habito);

        assertNull(historial.versionEl(LUNES));
        assertEquals(Objetivo.de(habito), historial.objetivoEl(LUNES));
        assertSame(habito, historial.vistaEl(LUNES));
        assertNull(historial.programadaTras(LUNES));
        // Y un mapa que no trae al hábito es lo mismo.
        assertEquals(Objetivo.de(habito), HistorialObjetivos.de(Map.of(), habito).objetivoEl(LUNES));
    }

    @Test
    void laVistaDeUnDiaLlevaSuObjetivoYLoDemasDelHabito_sinTocarElHabito() {
        Habito habito = habito();
        HistorialObjetivos historial = tresVersiones(habito);

        Habito delJueves = historial.vistaEl(JUEVES);

        assertNotSame(habito, delJueves);
        assertEquals(Frecuencia.DIARIO, delJueves.getFrecuencia());
        assertEquals(2, delJueves.getMeta());
        assertEquals(habito.getHabitoId(), delJueves.getHabitoId());
        assertEquals(INICIO, delJueves.getFechaInicio());
        assertEquals("Leer", delJueves.getNombre());
        // El hábito sigue con su objetivo de hoy.
        assertEquals(Frecuencia.SEMANAL, habito.getFrecuencia());
        assertEquals(3, habito.getMeta());
        // Con el mismo objetivo, la vista es el propio hábito.
        assertSame(habito, historial.vistaEl(LUNES_SIGUIENTE));
    }

    @Test
    void elCambioProgramadoEsLaPrimeraVersionPosteriorAHoy() {
        HistorialObjetivos historial = tresVersiones(habito());

        assertEquals(LUNES_SIGUIENTE, historial.programadaTras(JUEVES).getDesde());
        assertNull(historial.programadaTras(LUNES_SIGUIENTE));
    }

    @Test
    void unosDiasFijosEnBlanco_valenLoMismoQueNinguno() {
        assertEquals(new Objetivo(Frecuencia.SEMANAL, 3, null), new Objetivo(Frecuencia.SEMANAL, 3, "  "));
    }

    // ── Días hechos con varias versiones ──────────────────────────────

    @Test
    void diasHechos_cadaDiaSeJuzgaConElObjetivoQueTenia() {
        HistorialObjetivos historial = tresVersiones(habito());
        List<LocalDate> fechas = List.of(
                // Lunes y martes, una vez cada uno, con meta 1: dos días hechos.
                LUNES, LUNES.plusDays(1),
                // El jueves, una vez con meta 2: no llega.
                JUEVES,
                // El viernes, dos veces con meta 2: un día hecho.
                JUEVES.plusDays(1), JUEVES.plusDays(1),
                // Semanal desde el lunes siguiente: cada sesión es un día.
                LUNES_SIGUIENTE, LUNES_SIGUIENTE.plusDays(2));

        assertEquals(5, CalculoPremio.diasHechos(fechas, historial::objetivoEl));
        // Con un solo objetivo para todo (la copia de hoy, semanal) serían 6:
        // el jueves contaría aunque no llegó a su meta de entonces.
        assertEquals(6, CalculoPremio.diasHechos(Frecuencia.SEMANAL, 3, fechas));
    }

    @Test
    void diasHechos_subirLaMetaNoQuitaLosDiasQueLlegaronALaDeEntonces() {
        Habito habito = habito();
        HistorialObjetivos historial = HistorialObjetivos.de(habito, List.of(
                version(habito, INICIO, Frecuencia.DIARIO, 1),
                version(habito, JUEVES, Frecuencia.DIARIO, 2)));
        List<LocalDate> cincoDias = List.of(LUNES.minusDays(7), LUNES.minusDays(6),
                LUNES.minusDays(5), LUNES.minusDays(4), LUNES.minusDays(3));

        assertEquals(5, CalculoPremio.diasHechos(cincoDias, historial::objetivoEl));
        assertEquals(0, CalculoPremio.diasHechos(Frecuencia.DIARIO, 2, cincoDias));
    }
}
