package com.norday.habitos;

import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Cómo va hoy el usuario, para el ánimo de la mascota: secciones 12, 13 y 24
 * de las decisiones del rediseño. "Hoy" es el sábado 10-oct-2026 y el lunes
 * de esa semana, el 5. Estar ausente son tres días enteros sin hacer nada;
 * con solo semanales, una semana entera.
 */
class ComoVaHoyTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LocalDate.of(2026, 10, 6);
    private static final LocalDate MIERCOLES = LocalDate.of(2026, 10, 7);
    private static final LocalDate VIERNES = LocalDate.of(2026, 10, 9);
    private static final LocalDate HOY = LocalDate.of(2026, 10, 10);

    private static Habito habito(int id, Frecuencia frecuencia, int meta, String diasSemana) {
        Habito habito = new Habito();
        habito.setHabitoId(id);
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setFechaInicio(LocalDate.of(2026, 1, 1));
        return habito;
    }

    private final Habito leer = habito(1, Frecuencia.DIARIO, 1, null);
    private final Habito agua = habito(2, Frecuencia.DIARIO, 4, null);
    private final Habito gimnasio = habito(3, Frecuencia.SEMANAL, 5, null);
    private final Habito yoga = habito(4, Frecuencia.SEMANAL, 3, "2,4,6");
    private final Habito pilates = habito(5, Frecuencia.SEMANAL, 2, "1,3");
    private final List<Habito> diariosYGimnasio = List.of(leer, agua, gimnasio);

    @Test
    void conTodoLoDelDiaHecho_esCompleto_aunqueQuedeAlgoDeLaSemana() {
        assertEquals(AvanceDeHoy.COMPLETO, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(1, List.of(HOY), 2, List.of(HOY, HOY, HOY, HOY)), List.of(HOY), HOY));
    }

    @Test
    void unPasoParcialOAlgoDeLaSemana_esEmpezado() {
        // Un vaso de cuatro.
        assertEquals(AvanceDeHoy.EMPEZADO, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(2, List.of(HOY)), List.of(HOY), HOY));
        // Solo una sesión de gimnasio, que va en la sección de la semana.
        assertEquals(AvanceDeHoy.EMPEZADO, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(3, List.of(HOY)), List.of(HOY), HOY));
    }

    @Test
    void sinNadaHoy_esSinEmpezar_yAusenteTrasTresDiasEnterosSinNada() {
        // Lo último, ayer.
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(1, List.of(VIERNES)), List.of(VIERNES), HOY));
        // Lo último, el miércoles: jueves y viernes sin nada, dos días.
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(1, List.of(MIERCOLES)), List.of(MIERCOLES), HOY));
        // Lo último, el martes: miércoles, jueves y viernes sin nada.
        assertEquals(AvanceDeHoy.AUSENTE, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(1, List.of(MARTES)), List.of(MARTES), HOY));
    }

    @Test
    void quienAcabaDeLlegar_noEstaAusente() {
        // Nunca ha hecho nada: la referencia es el día en que llegó.
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(), List.of(VIERNES), HOY));
        assertEquals(AvanceDeHoy.AUSENTE, CalculoHoy.comoVa(diariosYGimnasio,
                Map.of(), List.of(LocalDate.of(2026, 9, 30)), HOY));
    }

    @Test
    void conSoloSemanales_seMidePorSemanas() {
        List<Habito> soloGimnasio = List.of(gimnasio);

        // Hizo una sesión el lunes: esta semana ya ha empezado.
        assertEquals(AvanceDeHoy.EMPEZADO, CalculoHoy.comoVa(soloGimnasio,
                Map.of(3, List.of(LUNES)), List.of(LUNES), HOY));
        // Nada esta semana, pero sí la pasada (miércoles 30-sep).
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, CalculoHoy.comoVa(soloGimnasio,
                Map.of(), List.of(LocalDate.of(2026, 9, 30)), HOY));
        // Nada esta semana ni la pasada: lo último, el martes 22-sep.
        assertEquals(AvanceDeHoy.AUSENTE, CalculoHoy.comoVa(soloGimnasio,
                Map.of(), List.of(LocalDate.of(2026, 9, 22)), HOY));
    }

    @Test
    void unSemanalConDiaFijo_enSuDiaCuentaParaCompletarElDia() {
        List<Habito> soloYoga = List.of(yoga);

        // Yoga toca martes, jueves y sábado. Hecho hoy, sábado.
        assertEquals(AvanceDeHoy.COMPLETO, CalculoHoy.comoVa(soloYoga,
                Map.of(4, List.of(HOY)), List.of(HOY), HOY));
        // Hoy no, pero sí el martes: la semana está empezada y el día no está completo.
        assertEquals(AvanceDeHoy.EMPEZADO, CalculoHoy.comoVa(soloYoga,
                Map.of(4, List.of(MARTES)), List.of(MARTES), HOY));
    }

    @Test
    void unDiaSinSeccionDelDia_nuncaEsCompleto() {
        // Pilates toca lunes y miércoles, y están hechos. Hoy, sábado, no toca nada.
        assertEquals(AvanceDeHoy.EMPEZADO, CalculoHoy.comoVa(List.of(pilates),
                Map.of(5, List.of(LUNES, MIERCOLES)), List.of(LUNES, MIERCOLES), HOY));
    }

    @Test
    void sinHabitos_soloCuentaCuantoHaceQueLlego() {
        assertEquals(AvanceDeHoy.SIN_EMPEZAR, CalculoHoy.comoVa(List.of(),
                Map.of(), List.of(VIERNES), HOY));
        assertEquals(AvanceDeHoy.AUSENTE, CalculoHoy.comoVa(List.of(),
                Map.of(), List.of(LocalDate.of(2026, 9, 10)), HOY));
    }
}
