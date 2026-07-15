package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;
import com.joaquim.habitosapp.model.Registro;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.dto.HabitoDetalleDTO;
import com.joaquim.habitosapp.model.dto.RegistroDiaDTO;
import com.joaquim.habitosapp.model.dto.RegistroResumenDTO;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class HabitoService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private MotorLogrosService motorLogrosService;

    public List<String> crearHabito(Habito habito) {
        habito.setFechaInicio(LocalDate.now());
        habito.setActivo(true);
        habitoDAO.save(habito);
        Racha racha = new Racha(habito);
        rachaDAO.save(racha);

        return motorLogrosService.evaluarTrasCrearHabito(habito.getPropietario());
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

    public void actualizar(Habito habito) {
        Habito existente = habitoDAO.findById(habito.getHabitoId());
        if (existente == null) {
            throw new RuntimeException("Hábito no encontrado");
        }

        boolean cambioFrecuencia = existente.getFrecuencia() != habito.getFrecuencia();

        habito.setFechaInicio(existente.getFechaInicio());
        habito.setActivo(existente.isActivo());
        habitoDAO.update(habito);

        if (cambioFrecuencia) {
            Racha racha = rachaDAO.findByHabito(habito);
            if (racha != null) {
                racha.setRachaActual(0);
                racha.setMetaAlcanzadaPeriodoActual(false);
                rachaDAO.update(racha);
            }
        }

        motorLogrosService.evaluarTrasEditarHabito(habito.getPropietario());
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

        motorLogrosService.evaluarTrasVerDetalleHabito(habito.getPropietario());

        if (mes == null) {
            mes = YearMonth.now();
        }

        Racha racha = rachaDAO.findByHabito(habito);
        List<Registro> todosRegistros = registroDAO.findByHabito(habito);

        LocalDate desde = mes.atDay(1);
        LocalDate hasta = mes.atEndOfMonth();
        List<Registro> registrosMes = registroDAO.findByHabitoAndRango(habito, desde, hasta);

        List<RegistroDiaDTO> heatmap = new ArrayList<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            LocalDate fechaActual = dia;
            boolean completado = registrosMes.stream()
                    .anyMatch(r -> r.getFecha().equals(fechaActual) && r.isCompletado());
            heatmap.add(new RegistroDiaDTO(fechaActual, completado));
        }

        int completadosMesActual = (int) registrosMes.stream()
                .filter(Registro::isCompletado)
                .count();

        Double porcentaje = null;
        if (habito.getFrecuencia().name().equals("DIARIO")) {
            int diasTranscurridos = Math.min(LocalDate.now().getDayOfMonth(), mes.lengthOfMonth());
            if (mes.equals(YearMonth.now()) && diasTranscurridos > 0) {
                porcentaje = (completadosMesActual * 100.0) / diasTranscurridos;
            } else if (!mes.equals(YearMonth.now())) {
                porcentaje = (completadosMesActual * 100.0) / mes.lengthOfMonth();
            }
        }

        List<RegistroResumenDTO> ultimosRegistros = todosRegistros.stream()
                .limit(10)
                .map(r -> new RegistroResumenDTO(r.getRegistroId(), r.getFecha(), r.isCompletado(), r.getNota()))
                .collect(Collectors.toList());

        HabitoDetalleDTO dto = new HabitoDetalleDTO();
        dto.setHabitoId(habito.getHabitoId());
        dto.setNombre(habito.getNombre());
        dto.setRachaActual(racha != null ? racha.getRachaActual() : 0);
        dto.setRachaMaxima(racha != null ? racha.getRachaMaxima() : 0);
        dto.setTotalCompletados((int) todosRegistros.stream().filter(Registro::isCompletado).count());
        dto.setMeta(habito.getMeta());
        dto.setFrecuencia(habito.getFrecuencia().name());
        dto.setCompletadosMesActual(completadosMesActual);
        dto.setPorcentajeMesActual(porcentaje);
        dto.setMesConsultado(mes.toString());
        dto.setHeatmap(heatmap);
        dto.setUltimosRegistros(ultimosRegistros);

        return dto;
    }
}