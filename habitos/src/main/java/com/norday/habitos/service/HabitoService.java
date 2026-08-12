package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.dto.DashboardHabitoDTO;
import com.norday.habitos.model.dto.HabitoDetalleDTO;
import com.norday.habitos.model.dto.HabitoResumenDTO;
import com.norday.habitos.model.dto.RegistroDiaDTO;
import com.norday.habitos.model.dto.RegistroResumenDTO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;

@Service
public class HabitoService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private RachaService rachaService;

    @Autowired
    private com.norday.core.service.ZonaUsuarioService zonaUsuarioService;

    /** Normaliza la relación frecuencia/díasSemana/meta antes de guardar:
     *  los días solo aplican a SEMANAL, y si hay días la meta se deriva de ellos. */
    private void normalizarPlanificacion(Habito habito) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) {
            habito.setDiasSemana(null);
            return;
        }
        String dias = habito.getDiasSemana();
        if (dias != null && !dias.isBlank()) {
            habito.setMeta((int) java.util.Arrays.stream(dias.split(","))
                    .filter(d -> !d.isBlank()).count());
        }
    }

    public List<String> crearHabito(Habito habito) {
        normalizarPlanificacion(habito);
        LocalDate hoy = LocalDate.now(rachaService.zonaDe(habito));
        habito.setFechaInicio(hoy);
        habito.setActivo(true);
        habitoDAO.save(habito);
        Racha racha = new Racha(habito, hoy);
        rachaDAO.save(racha);

        return logrosHabitosService.evaluarTrasCrearHabito(habito.getPropietario());
    }

    public Habito buscarPorId(int id) {
        return habitoDAO.findById(id);
    }

    public List<Habito> obtenerTodos(Usuario propietario) {
        return habitoDAO.findByPropietario(propietario);
    }

    public List<Habito> obtenerActivos(Usuario propietario) {
        return habitoDAO.findActivos(propietario);
    }

    public List<Habito> obtenerInactivos(Usuario propietario) {
        return habitoDAO.findInactivos(propietario);
    }

    public List<HabitoResumenDTO> obtenerResumen(Usuario propietario) {
        List<Habito> todos = habitoDAO.findByPropietario(propietario);
        Map<Integer, Long> conteos = registroDAO.contarCompletadosPorUsuario(propietario.getUsuarioId());

        List<HabitoResumenDTO> resumen = new ArrayList<>();
        for (Habito habito : todos) {
            long total = conteos.getOrDefault(habito.getHabitoId(), 0L);
            resumen.add(new HabitoResumenDTO(habito, (int) total));
        }
        return resumen;
    }

    public void actualizar(Habito habito) {
        Habito existente = habitoDAO.findById(habito.getHabitoId());
        if (existente == null) {
            throw new RuntimeException("Hábito no encontrado");
        }

        boolean cambioFrecuencia = existente.getFrecuencia() != habito.getFrecuencia();

        habito.setFechaInicio(existente.getFechaInicio());
        habito.setActivo(existente.isActivo());
        normalizarPlanificacion(habito);
        habitoDAO.update(habito);

        if (cambioFrecuencia) {
            Racha racha = rachaDAO.findByHabito(habito);
            if (racha != null) {
                racha.setRachaActual(0);
                racha.setPeriodoMetaAlcanzada(null);
                rachaDAO.update(racha);
            }
        }
    }

    public void activar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito != null) {
            habito.setActivo(true);
            habitoDAO.update(habito);
        }
    }

    public void desactivar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito != null) {
            habito.setActivo(false);
            habitoDAO.update(habito);
        }
    }

    public void eliminar(int id) {
        registroDAO.deleteByHabito(id);
        rachaDAO.deleteByHabito(id);
        habitoDAO.delete(id);
    }

    public HabitoDetalleDTO obtenerDetalle(int habitoId, YearMonth mes) {
        Habito habito = habitoDAO.findById(habitoId);
        if (habito == null) {
            throw new RuntimeException("Hábito no encontrado");
        }

        if (mes == null) {
            mes = YearMonth.now(rachaService.zonaDe(habito));
        }

        ZoneId zona = rachaService.zonaDe(habito);
        Racha racha = rachaDAO.findByHabito(habito);
        List<Registro> todosRegistros = registroDAO.findByHabito(habito);

        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        List<Registro> registrosMes = registroDAO.findByHabitoAndRango(habito, desde, hasta);

        List<RegistroDiaDTO> heatmap = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            LocalDate fechaActual = dia;
            int veces = (int) registrosMes.stream()
                    .filter(r -> r.getFecha().equals(fechaActual) && r.isCompletado())
                    .count();
            heatmap.add(new RegistroDiaDTO(fechaActual, veces > 0, veces));
        }

        // Días distintos con al menos un completado (no registros: un diario
        // con meta 3/día que se hace 3 veces el lunes cuenta como 1 día)
        int completadosMesActual = (int) registrosMes.stream()
                .filter(Registro::isCompletado)
                .map(Registro::getFecha)
                .distinct()
                .count();

        YearMonth mesActual = YearMonth.now(zona);
        Double porcentaje = null;
        if (habito.getFrecuencia().name().equals("DIARIO")) {
            int diasTranscurridos = Math.min(LocalDate.now(zona).getDayOfMonth(), mes.lengthOfMonth());
            if (mes.equals(mesActual) && diasTranscurridos > 0) {
                porcentaje = (completadosMesActual * 100.0) / diasTranscurridos;
            } else if (!mes.equals(mesActual)) {
                porcentaje = (completadosMesActual * 100.0) / mes.lengthOfMonth();
            }
        }

        List<RegistroResumenDTO> ultimosRegistros = todosRegistros.stream()
                .limit(10)
                .map(r -> new RegistroResumenDTO(r.getRegistroId(), r.getFecha(), r.isCompletado(), r.getNota(), r.getValoracion()))
                .collect(Collectors.toList());

        var valoracionStats = todosRegistros.stream()
                .filter(r -> r.getValoracion() != null)
                .mapToInt(Registro::getValoracion)
                .average();
        Double valoracionMedia = valoracionStats.isPresent() ? valoracionStats.getAsDouble() : null;

        HabitoDetalleDTO dto = new HabitoDetalleDTO();
        dto.setHabitoId(habito.getHabitoId());
        dto.setNombre(habito.getNombre());
        dto.setRachaActual(rachaService.rachaActualVigente(racha));
        dto.setRachaMaxima(racha != null ? racha.getRachaMaxima() : 0);
        dto.setTotalCompletados((int) todosRegistros.stream().filter(Registro::isCompletado).count());
        dto.setMeta(habito.getMeta());
        dto.setFrecuencia(habito.getFrecuencia().name());
        dto.setDiasSemana(habito.getDiasSemana());
        dto.setCompletadosMesActual(completadosMesActual);
        dto.setPorcentajeMesActual(porcentaje);
        dto.setMesConsultado(mes.toString());
        dto.setHeatmap(heatmap);
        dto.setUltimosRegistros(ultimosRegistros);
        dto.setValoracionMedia(valoracionMedia);

        return dto;
    }

    /**
     * ¿Ha cumplido hoy el usuario todo lo que tenía comprometido?
     *
     * Solo miran los DIARIO. Un SEMANAL no tiene un día en el que "toque" —su
     * meta es flexible dentro de la semana—, así que uno a medias no significa
     * que hoy quede algo pendiente. Contarlo bloqueaba el día completo los
     * siete días de la semana aunque los diarios estuvieran todos hechos.
     *
     * Sin hábitos activos no hay día completo: no había nada que cumplir.
     * Tener solo semanales es distinto: hoy no había nada que cumplir, y eso
     * sí cuenta como cumplido.
     */
    public boolean esDiaCompleto(Usuario usuario) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        if (activos.isEmpty()) return false;

        List<Habito> diarios = activos.stream()
                .filter(h -> h.getFrecuencia() == Frecuencia.DIARIO)
                .toList();
        if (diarios.isEmpty()) return true;

        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        for (Habito habito : diarios) {
            LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(zona);
            int completadosPeriodo =
                    registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
            if (completadosPeriodo < habito.getMeta()) return false;
        }
        return true;
    }

    /**
     * ¿Le toca a {@code habito} el día {@code fecha}?
     *
     * DIARIO siempre toca. SEMANAL sin días fijos (diasSemana null o en
     * blanco) es "semanal flexible": toca cualquier día, igual que un hábito
     * sin planificación concreta. SEMANAL con días fijos solo toca si el día
     * ISO de {@code fecha} (1=lunes...7=domingo, ver
     * {@link LocalDate#getDayOfWeek()} y el comentario de
     * {@link Habito#getDiasSemana()}) está en la lista separada por comas.
     * Cada trozo se recorta antes de comparar, así que " 2, 4 " vale igual
     * que "2,4".
     */
    private boolean estaProgramadoPara(Habito habito, LocalDate fecha) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) return true;

        String dias = habito.getDiasSemana();
        if (dias == null || dias.isBlank()) return true;

        String diaIso = String.valueOf(fecha.getDayOfWeek().getValue());
        for (String trozo : dias.split(",")) {
            if (trozo.trim().equals(diaIso)) return true;
        }
        return false;
    }

    public List<DashboardHabitoDTO> obtenerDashboard(Usuario usuario) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        List<DashboardHabitoDTO> dashboard = new ArrayList<>();
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        LocalDate hoy = LocalDate.now(zona);

        // Ventana: los 10 días de la mini-heatmap, extendida hacia atrás hasta
        // el lunes de la semana del día más antiguo (para calcular "semana
        // cumplida" con la semana completa). Máximo 16 días.
        LocalDate baseVentana = hoy.minusDays(9);
        LocalDate inicioVentana =
                baseVentana.minusDays(baseVentana.getDayOfWeek().getValue() - 1L);

        for (Habito habito : activos) {
            if (!estaProgramadoPara(habito, hoy)) continue;

            // Una sola consulta acotada por hábito
            List<Registro> registrosVentana =
                    registroDAO.findByHabitoAndRango(habito, inicioVentana, hoy);

            LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(zona);

            int completadosPeriodo = (int) registrosVentana.stream()
                    .filter(r -> !r.getFecha().isBefore(periodo[0]) && !r.getFecha().isAfter(periodo[1]))
                    .count();

            boolean completadoHoy = registrosVentana.stream()
                    .anyMatch(r -> r.getFecha().equals(hoy));

            List<String> fechasCompletadas = registrosVentana.stream()
                    .filter(Registro::isCompletado)
                    .map(r -> r.getFecha().toString())
                    .collect(Collectors.toList());

            dashboard.add(new DashboardHabitoDTO(habito, completadoHoy, completadosPeriodo, fechasCompletadas));
        }
        return dashboard;
    }
}