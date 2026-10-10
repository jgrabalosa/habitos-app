package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.dto.DiaTiraDTO;
import com.norday.habitos.model.dto.FilaHoyDTO;
import com.norday.habitos.model.dto.HabitoDTO;
import com.norday.habitos.model.dto.HoyDTO;
import com.norday.habitos.model.dto.SeccionHoyDTO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Arma la pantalla de Hoy de la app nueva: las dos secciones, la tira de la
 * semana, el avance del día y los niveles de ritmo. Las reglas viven en
 * {@link CalculoHoy} y {@link CalculoRitmo}; aquí solo se leen los datos y
 * se reparten.
 *
 * Tres consultas sea cual sea el número de hábitos: los hábitos activos,
 * los registros de la semana y —dentro de RitmoService— las anclas y los
 * registros que haga falta repasar.
 *
 * No sustituye a HabitoService.obtenerDashboard ni a obtenerSemana: esos dos
 * los sigue usando la app ya instalada, con sus reglas de siempre.
 */
@Service
public class HoyService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private RitmoService ritmoService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /** Hoy tal como lo ve el usuario; con {@code fechaPedida}, otro día de la tira. */
    @Transactional
    public HoyDTO obtenerHoy(Usuario usuario, LocalDate fechaPedida) {
        LocalDate hoy = LocalDate.now(zonaUsuarioService.zonaDe(usuario));
        return obtenerHoy(usuario, fechaPedida != null ? fechaPedida : hoy, hoy);
    }

    /** Igual, con el día de hoy dado, para que los tests lo fijen. */
    @Transactional
    public HoyDTO obtenerHoy(Usuario usuario, LocalDate fecha, LocalDate hoy) {
        LocalDate lunes = fecha.with(DayOfWeek.MONDAY);
        List<Habito> activos = habitoDAO.findActivos(usuario);

        Map<Integer, List<LocalDate>> fechasPorHabito = new HashMap<>();
        for (Registro registro : registroDAO.findByHabitosAndRango(activos, lunes, lunes.plusDays(6))) {
            if (!registro.isCompletado()) continue;
            fechasPorHabito
                    .computeIfAbsent(registro.getHabito().getHabitoId(), id -> new ArrayList<>())
                    .add(registro.getFecha());
        }

        // El ritmo es siempre el de hoy, aunque se mire otro día de la tira.
        Map<Integer, Double> ritmos = ritmoService.ritmosVigentes(activos, hoy);

        List<FilaHoyDTO> delDia = new ArrayList<>();
        List<FilaHoyDTO> deLaSemana = new ArrayList<>();
        for (Habito habito : activos) {
            List<LocalDate> fechas = fechasPorHabito.getOrDefault(habito.getHabitoId(), List.of());
            int nivel = CalculoRitmo.nivel(ritmos.get(habito.getHabitoId()));

            if (CalculoHoy.tocaEl(habito, fecha)) {
                int completados = habito.getFrecuencia() == Frecuencia.DIARIO
                        ? CalculoHoy.hechosEl(fechas, fecha)
                        : fechas.size();
                delDia.add(fila(habito, completados,
                        CalculoHoy.hechoEnElDia(habito, fechas, fecha), fechas, fecha, nivel));
            } else if (CalculoHoy.vaEnLaSemanaDe(habito, fecha)) {
                deLaSemana.add(fila(habito, fechas.size(),
                        CalculoHoy.hechoEnLaSemana(habito, fechas), fechas, fecha, nivel));
            }
        }

        List<DiaTiraDTO> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate dia = lunes.plusDays(i);
            Double avance = dia.isAfter(hoy)
                    ? null
                    : oNull(CalculoHoy.avanceDelDia(activos, fechasPorHabito, dia));
            dias.add(new DiaTiraDTO(dia.toString(), avance));
        }

        OptionalDouble general = ritmoService.generalDe(activos, ritmos, hoy);
        Integer nivelGeneral = general.isPresent() ? CalculoRitmo.nivel(general.getAsDouble()) : null;

        return new HoyDTO(fecha.toString(), hoy.toString(), dias,
                oNull(CalculoHoy.avanceDelDia(activos, fechasPorHabito, fecha)),
                nivelGeneral, seccion(delDia), seccion(deLaSemana));
    }

    private FilaHoyDTO fila(Habito habito, int completados, boolean hecho,
                            List<LocalDate> fechas, LocalDate fecha, int nivel) {
        return new FilaHoyDTO(HabitoDTO.desde(habito), completados, habito.getMeta(), hecho,
                fechas.contains(fecha), nivel,
                fechas.stream().map(LocalDate::toString).toList());
    }

    private SeccionHoyDTO seccion(List<FilaHoyDTO> filas) {
        int hechos = (int) filas.stream().filter(FilaHoyDTO::hecho).count();
        return new SeccionHoyDTO(hechos, filas.size(), filas);
    }

    private Double oNull(OptionalDouble valor) {
        return valor.isPresent() ? valor.getAsDouble() : null;
    }
}
