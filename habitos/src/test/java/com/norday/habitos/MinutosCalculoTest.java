package com.norday.habitos;

import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.CalculoObjetivo;
import com.norday.habitos.model.CalculoObjetivo.PeriodoDelCambio;
import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Unidad;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Los cálculos que juzgan un día con hábitos de minutos: días hechos,
 * crédito, Hoy, recordatorios, ritmo y regla de cambios. La semana de
 * referencia empieza el lunes 5-oct-2026. Las cifras salen del diseño
 * (DISENO_MINUTOS.md, secciones 1 a 4), no del código.
 */
class MinutosCalculoTest {

    private static final LocalDate LUNES = LocalDate.of(2026, 10, 5);
    private static final LocalDate MARTES = LUNES.plusDays(1);
    private static final LocalDate MIERCOLES = LUNES.plusDays(2);
    private static final LocalDate JUEVES = LUNES.plusDays(3);
    private static final LocalDate VIERNES = LUNES.plusDays(4);
    private static final LocalDate INICIO = LocalDate.of(2026, 1, 1);

    private static final Objetivo MIN_DIARIO_30 = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 30, null);
    private static final Objetivo MIN_DIARIO_40 = new Objetivo(Unidad.MINUTOS, Frecuencia.DIARIO, 1, 40, null);
    private static final Objetivo MIN_SEMANAL_3x60 = new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, null);
    private static final Objetivo MIN_SEMANAL_3x45 = new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 45, null);
    private static final Objetivo MIN_SEMANAL_4x45 = new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 4, 45, null);
    private static final Objetivo VECES_DIARIO_3 = new Objetivo(Frecuencia.DIARIO, 3, null);
    private static final Objetivo VECES_SEMANAL_3 = new Objetivo(Frecuencia.SEMANAL, 3, null);

    private static Habito habito(Objetivo objetivo) {
        Habito habito = new Habito("Leer", null, objetivo.frecuencia(), objetivo.meta(), null, null, INICIO);
        habito.setHabitoId(7);
        habito.setUnidad(objetivo.unidad());
        habito.setMinutosObjetivo(objetivo.minutosObjetivo());
        habito.setDiasSemana(objetivo.diasSemana());
        return habito;
    }

    private static Registro registro(LocalDate fecha, int minutos) {
        Registro registro = new Registro(null, true, null, fecha);
        registro.setCantidad(minutos);
        return registro;
    }

    private static Hechos hechos(Objetivo objetivo, Registro... registros) {
        return Hechos.de(List.of(registros), fecha -> objetivo);
    }

    // ── Días hechos ──────────────────────────────────────────────

    @Test
    void unDiaDeMinutos_estaHechoCuandoLaSumaLlegaAlObjetivo() {
        Hechos h = hechos(MIN_DIARIO_30, registro(LUNES, 10), registro(LUNES, 20),
                registro(MARTES, 29), registro(MIERCOLES, 45));
        assertEquals(30, h.cantidadEl(LUNES));
        assertTrue(h.diaHecho(LUNES));
        assertFalse(h.diaHecho(MARTES), "29 de 30 es un día parcial");
        assertTrue(h.algoEl(MARTES), "pero algo se hizo");
        assertTrue(h.diaHecho(MIERCOLES), "pasarse también lo hace");
        assertFalse(h.algoEl(JUEVES));
        assertEquals(2, h.diasHechos());
        assertEquals(2, CalculoPremio.diasHechos(h));
        // En minutos la unidad del periodo son días: 1 por día hecho.
        assertEquals(1, h.unidadesEl(LUNES));
        assertEquals(0, h.unidadesEl(MARTES));
        assertEquals(2, h.unidades());
        assertEquals(1, h.unidadesHasta(MARTES));
        assertEquals(List.of(LUNES, MIERCOLES), h.fechasEnUnidades());
        assertEquals(List.of(LUNES, MIERCOLES), h.fechasHechas());
    }

    @Test
    void cadaDiaSeJuzgaConSusMinutos() {
        // Hasta el martes 30 min; desde el miércoles 40.
        Function<LocalDate, Objetivo> objetivoEl = f -> f.isBefore(MIERCOLES) ? MIN_DIARIO_30 : MIN_DIARIO_40;
        Hechos h = Hechos.de(List.of(registro(LUNES, 30), registro(MIERCOLES, 30), registro(JUEVES, 40)),
                objetivoEl);
        assertTrue(h.diaHecho(LUNES));
        assertFalse(h.diaHecho(MIERCOLES));
        assertTrue(h.diaHecho(JUEVES));
        assertEquals(2, CalculoPremio.diasHechos(h));
    }

    @Test
    void enVecesLosHechosSonLasVecesComoAntes() {
        Hechos h = Hechos.deFechas(List.of(LUNES, LUNES, MARTES), VECES_DIARIO_3);
        assertEquals(2, h.cantidadEl(LUNES));
        assertEquals(2, h.unidadesEl(LUNES));
        assertEquals(3, h.unidades());
        assertFalse(h.diaHecho(LUNES));
        assertEquals(List.of(LUNES, LUNES, MARTES), h.fechasEnUnidades());
        assertEquals(List.of(LUNES, MARTES), h.fechasHechas());
        Hechos semanal = Hechos.deFechas(List.of(LUNES, MARTES), VECES_SEMANAL_3);
        assertTrue(semanal.diaHecho(LUNES));
        assertEquals(2, CalculoPremio.diasHechos(semanal));
    }

    @Test
    void anadirUnPaso_noTocaElOriginal() {
        Hechos antes = hechos(MIN_DIARIO_30, registro(LUNES, 20));
        Hechos despues = antes.con(LUNES, 10);
        assertFalse(antes.diaHecho(LUNES));
        assertTrue(despues.diaHecho(LUNES));
        assertEquals(Map.of(LUNES, 30), despues.porDia());
    }

    // ── Hoy ──────────────────────────────────────────────────────

    @Test
    void hoy_diarioDeMinutos_hechoAlLlegar_creditoParcial() {
        Habito diario = habito(MIN_DIARIO_30);
        Hechos h = hechos(MIN_DIARIO_30, registro(LUNES, 20), registro(MARTES, 45));
        assertFalse(CalculoHoy.hechoEnElDia(diario, h, LUNES));
        assertTrue(CalculoHoy.hechoEnElDia(diario, h, MARTES));
        assertEquals(20.0 / 30.0, CalculoHoy.creditoDelDia(diario, h, LUNES), 1e-9);
        assertEquals(1.0, CalculoHoy.creditoDelDia(diario, h, MARTES), 1e-9, "pasarse no suma más de 1");
        assertEquals(0.0, CalculoHoy.creditoDelDia(diario, h, MIERCOLES), 1e-9);
    }

    @Test
    void hoy_semanalDeMinutos_cuentaDiasHechos_noMinutosAcumulados() {
        Habito semanal = habito(MIN_SEMANAL_3x60);
        // 180 minutos en la semana, pero solo un día llega a 60: no está hecha.
        Hechos h = hechos(MIN_SEMANAL_3x60, registro(LUNES, 30), registro(LUNES, 30),
                registro(MARTES, 59), registro(MIERCOLES, 61));
        assertEquals(2, h.unidades());
        assertFalse(CalculoHoy.hechoEnLaSemana(semanal, h));
        Hechos completa = h.con(JUEVES, 60);
        assertTrue(CalculoHoy.hechoEnLaSemana(semanal, completa));
    }

    @Test
    void hoy_semanalDeMinutosConDiasFijos_cubiertoSoloConElDiaHecho() {
        Objetivo lxv = new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, "1,3,5");
        Habito semanal = habito(lxv);
        Hechos parcial = hechos(lxv, registro(LUNES, 30));
        assertFalse(CalculoHoy.cubierto(semanal, parcial, LUNES), "30 de 60 no cubre el lunes");
        assertEquals(0.0, CalculoHoy.creditoDelDia(semanal, parcial, LUNES), 1e-9);
        Hechos hecho = hechos(lxv, registro(LUNES, 60));
        assertTrue(CalculoHoy.cubierto(semanal, hecho, LUNES));
        assertTrue(CalculoHoy.hechoEnElDia(semanal, hecho, LUNES));
    }

    @Test
    void hoy_avanceDelDia_cuentaElDiarioDeMinutosEnProporcion() {
        Habito diario = habito(MIN_DIARIO_30);
        Habito otro = habito(VECES_DIARIO_3);
        otro.setHabitoId(8);
        Map<Integer, Hechos> porHabito = new HashMap<>();
        porHabito.put(7, hechos(MIN_DIARIO_30, registro(LUNES, 15)));
        porHabito.put(8, Hechos.deFechas(List.of(LUNES, LUNES, LUNES), VECES_DIARIO_3));
        assertEquals((0.5 + 1.0) / 2,
                CalculoHoy.avanceDelDiaConHechos(List.of(diario, otro), porHabito, LUNES).getAsDouble(), 1e-9);
    }

    @Test
    void comoVa_unDiaParcialDeMinutosEsEmpezado_noCompleto() {
        Habito diario = habito(MIN_DIARIO_30);
        Map<Integer, Hechos> porHabito = Map.of(7, hechos(MIN_DIARIO_30, registro(VIERNES, 10)));
        assertEquals(AvanceDeHoy.EMPEZADO,
                CalculoHoy.comoVaConHechos(List.of(diario), porHabito, List.of(VIERNES), VIERNES));
        Map<Integer, Hechos> hecho = Map.of(7, hechos(MIN_DIARIO_30, registro(VIERNES, 30)));
        assertEquals(AvanceDeHoy.COMPLETO,
                CalculoHoy.comoVaConHechos(List.of(diario), hecho, List.of(VIERNES), VIERNES));
    }

    @Test
    void recordatorio_unDiaDeMinutosNoEstaHechoHastaSuObjetivo() {
        Habito diario = habito(MIN_DIARIO_30);
        assertTrue(CalculoHoy.tocaRecordar(diario, hechos(MIN_DIARIO_30, registro(VIERNES, 29)), VIERNES));
        assertFalse(CalculoHoy.tocaRecordar(diario, hechos(MIN_DIARIO_30, registro(VIERNES, 30)), VIERNES));
        // Semanal flexible: un parcial de hoy no lo calla; un día hecho, sí.
        Habito semanal = habito(MIN_SEMANAL_3x60);
        assertTrue(CalculoHoy.tocaRecordar(semanal, hechos(MIN_SEMANAL_3x60, registro(VIERNES, 20)), VIERNES));
        assertFalse(CalculoHoy.tocaRecordar(semanal, hechos(MIN_SEMANAL_3x60, registro(VIERNES, 60)), VIERNES));
    }

    @Test
    void hoy_enVecesLasVersionesConHechosDanLoMismoQueLasDeFechas() {
        Habito diario = habito(VECES_DIARIO_3);
        List<LocalDate> fechas = List.of(LUNES, LUNES, MARTES, MARTES, MARTES);
        Hechos h = Hechos.deFechas(fechas, VECES_DIARIO_3);
        for (LocalDate dia : List.of(LUNES, MARTES, MIERCOLES)) {
            assertEquals(CalculoHoy.hechoEnElDia(diario, fechas, dia), CalculoHoy.hechoEnElDia(diario, h, dia));
            assertEquals(CalculoHoy.creditoDelDia(diario, fechas, dia), CalculoHoy.creditoDelDia(diario, h, dia));
            assertEquals(CalculoHoy.tocaRecordar(diario, fechas, dia), CalculoHoy.tocaRecordar(diario, h, dia));
        }
        Objetivo mxv = new Objetivo(Frecuencia.SEMANAL, 3, "1,3,5");
        Habito semanal = habito(mxv);
        List<LocalDate> sesiones = List.of(LUNES, MARTES);
        Hechos hs = Hechos.deFechas(sesiones, mxv);
        for (LocalDate dia : List.of(LUNES, MARTES, MIERCOLES, VIERNES)) {
            assertEquals(CalculoHoy.cubierto(semanal, sesiones, dia), CalculoHoy.cubierto(semanal, hs, dia));
            assertEquals(CalculoHoy.tocaRecordar(semanal, sesiones, dia), CalculoHoy.tocaRecordar(semanal, hs, dia));
        }
        assertEquals(CalculoHoy.hechoEnLaSemana(semanal, sesiones), CalculoHoy.hechoEnLaSemana(semanal, hs));
    }

    // ── Ritmo ────────────────────────────────────────────────────

    @Test
    void ritmo_diarioDeMinutos_elCreditoDelDiaEsParcial() {
        Map<LocalDate, Double> unidades = Map.of(LUNES, 15.0 / 30.0);
        double esperado = CalculoRitmo.cerrarPeriodo(0.2, 0.5, Frecuencia.DIARIO);
        double obtenido = CalculoRitmo.alDiaPorUnidades(0.2, LUNES, Frecuencia.DIARIO, 1, INICIO,
                unidades, MARTES).valor();
        assertEquals(esperado, obtenido, 1e-12);
    }

    @Test
    void ritmo_semanalDeMinutos_laSemanaEsLaSumaDeCreditosDiariosEntreLaMeta() {
        Map<LocalDate, Double> unidades = Map.of(LUNES, 1.0, MARTES, 0.5, MIERCOLES, 1.0);
        LocalDate martesSiguiente = LUNES.plusDays(8);
        double esperado = CalculoRitmo.cerrarPeriodo(0.2, 2.5 / 3, Frecuencia.SEMANAL);
        assertEquals(esperado, CalculoRitmo.alDiaPorUnidades(0.2, LUNES, Frecuencia.SEMANAL, 3, INICIO,
                unidades, martesSiguiente).valor(), 1e-12);
        // Cuatro días hechos con meta 3: el crédito se queda en 1.
        Map<LocalDate, Double> cuatro = Map.of(LUNES, 1.0, MARTES, 1.0, MIERCOLES, 1.0, JUEVES, 1.0);
        assertEquals(CalculoRitmo.cerrarPeriodo(0.2, 1.0, Frecuencia.SEMANAL),
                CalculoRitmo.alDiaPorUnidades(0.2, LUNES, Frecuencia.SEMANAL, 3, INICIO,
                        cuatro, martesSiguiente).valor(), 1e-12);
    }

    @Test
    void ritmo_enVecesPorUnidadesEsIgualQuePorFechas() {
        List<LocalDate> fechas = new ArrayList<>(List.of(LUNES, LUNES, MARTES, JUEVES, JUEVES, JUEVES));
        Map<LocalDate, Double> unidades = Map.of(LUNES, 2.0, MARTES, 1.0, JUEVES, 3.0);
        LocalDate hoy = LUNES.plusDays(10);
        for (Frecuencia frecuencia : Frecuencia.values()) {
            for (int meta = 1; meta <= 4; meta++) {
                assertEquals(
                        CalculoRitmo.alDia(0.37, LUNES, frecuencia, meta, INICIO, fechas, hoy),
                        CalculoRitmo.alDiaPorUnidades(0.37, LUNES, frecuencia, meta, INICIO, unidades, hoy),
                        frecuencia + " meta " + meta);
            }
        }
    }

    // ── Regla de cambios ─────────────────────────────────────────

    @Test
    void cambios_minutosYPasoAMinutos_sonDelDia_conParteSemanal_deLaSemana() {
        assertEquals(PeriodoDelCambio.DIA, CalculoObjetivo.periodoDelCambio(MIN_DIARIO_30, MIN_DIARIO_40));
        assertEquals(PeriodoDelCambio.DIA, CalculoObjetivo.periodoDelCambio(VECES_DIARIO_3, MIN_DIARIO_30));
        assertEquals(PeriodoDelCambio.DIA, CalculoObjetivo.periodoDelCambio(MIN_SEMANAL_3x60, MIN_SEMANAL_3x45));
        assertEquals(PeriodoDelCambio.DIA, CalculoObjetivo.periodoDelCambio(VECES_SEMANAL_3, MIN_SEMANAL_3x60));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(MIN_SEMANAL_3x60, MIN_SEMANAL_4x45));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(VECES_SEMANAL_3,
                new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 4, 60, null)));
        assertEquals(PeriodoDelCambio.SEMANA, CalculoObjetivo.periodoDelCambio(MIN_DIARIO_30, MIN_SEMANAL_3x60));
        assertEquals(PeriodoDelCambio.NINGUNO, CalculoObjetivo.periodoDelCambio(
                new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, "1,3,5"),
                new Objetivo(Unidad.MINUTOS, Frecuencia.SEMANAL, 3, 60, "5,3,1")));
    }

    @Test
    void cambios_deMinutos_conRegistrosHoyManana_sinEllosHoy() {
        assertEquals(VIERNES.plusDays(1), CalculoObjetivo.fechaDeEfecto(MIN_DIARIO_30, MIN_DIARIO_40,
                VIERNES, INICIO, List.of(VIERNES)));
        assertEquals(VIERNES, CalculoObjetivo.fechaDeEfecto(MIN_DIARIO_30, MIN_DIARIO_40,
                VIERNES, INICIO, List.of(LUNES, JUEVES)));
        assertEquals(VIERNES.plusDays(1), CalculoObjetivo.fechaDeEfecto(VECES_SEMANAL_3, MIN_SEMANAL_3x60,
                VIERNES, INICIO, List.of(VIERNES)));
        // Mezcla minutos y días por semana: todo al lunes siguiente.
        assertEquals(LUNES.plusWeeks(1), CalculoObjetivo.fechaDeEfecto(MIN_SEMANAL_3x60, MIN_SEMANAL_4x45,
                VIERNES, INICIO, List.of(MARTES)));
    }

    @Test
    void cambios_deMinutosAVeces_noSePermiten() {
        assertFalse(CalculoObjetivo.permitido(MIN_DIARIO_30, VECES_DIARIO_3));
        assertFalse(CalculoObjetivo.permitido(MIN_SEMANAL_3x60, VECES_SEMANAL_3));
        assertTrue(CalculoObjetivo.permitido(VECES_DIARIO_3, MIN_DIARIO_30));
        assertTrue(CalculoObjetivo.permitido(MIN_DIARIO_30, MIN_DIARIO_40));
        assertTrue(CalculoObjetivo.permitido(VECES_DIARIO_3, VECES_SEMANAL_3));
    }
}
