package com.norday.habitos;

import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Unidad;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Dos objetivos que juzgan igual cualquier día son iguales, aunque los datos
 * guardados no estén escritos igual: así editar solo el nombre de un hábito
 * con datos heredados incoherentes no crea una versión falsa.
 */
class ObjetivoNormalizadoTest {

    @Test
    void losDiasFijosSeComparanComoConjunto() {
        Objetivo a = new Objetivo(Frecuencia.SEMANAL, 2, "4,2");
        Objetivo b = new Objetivo(Frecuencia.SEMANAL, 2, "2,4");
        assertEquals(a, b);
        assertEquals(a.hashCode(), b.hashCode());
        assertEquals(new Objetivo(Frecuencia.SEMANAL, 2, " 2, 4 "), b);
    }

    @Test
    void losDiasFijosNoCuentanFueraDelSemanal() {
        Objetivo conDias = new Objetivo(Frecuencia.DIARIO, 1, "1,3");
        Objetivo sinDias = new Objetivo(Frecuencia.DIARIO, 1, null);
        assertEquals(conDias, sinDias);
        assertEquals(conDias.hashCode(), sinDias.hashCode());
    }

    @Test
    void enUnSemanalLosDiasDistintosSiguenSiendoDistintos() {
        assertNotEquals(new Objetivo(Frecuencia.SEMANAL, 3, "1,3,5"),
                new Objetivo(Frecuencia.SEMANAL, 3, "2,4,6"));
        assertNotEquals(new Objetivo(Frecuencia.SEMANAL, 3, "1,3,5"),
                new Objetivo(Frecuencia.SEMANAL, 3, null));
        assertNotEquals(new Objetivo(Frecuencia.SEMANAL, 3, null),
                new Objetivo(Frecuencia.SEMANAL, 4, null));
    }

    @Test
    void unaVistaDeUnObjetivoEquivalenteEsElMismoHabito() {
        Habito habito = new Habito("Leer", null, Frecuencia.SEMANAL, 2, null, null,
                java.time.LocalDate.of(2026, 1, 1));
        habito.setDiasSemana("4,2");
        assertSame(habito, habito.conObjetivo(new Objetivo(Frecuencia.SEMANAL, 2, "2,4")));
    }

    @Test
    void enUnDiarioDeMinutosLaMetaEsSiempreUno() {
        Objetivo heredado = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 3, 30, null);
        Objetivo forzado = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null);
        assertEquals(heredado, forzado);
        assertEquals(heredado.hashCode(), forzado.hashCode());
    }

    @Test
    void losMinutosSoloCuentanEnMinutos_yLaUnidadSiempre() {
        assertEquals(new Objetivo(Unidad.VECES, Frecuencia.DIARIO, 2, 30, null),
                new Objetivo(Frecuencia.DIARIO, 2, null));
        assertNotEquals(new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null),
                new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 40, null));
        assertNotEquals(new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null),
                new Objetivo(Frecuencia.DIARIO, 1, null));
        assertNotEquals(new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, null),
                new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 2, 60, null));
    }
}
