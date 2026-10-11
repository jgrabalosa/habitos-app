package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.gamificacion.service.CumplimientoDiarioPort;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Lo que el dominio "hábitos" le contesta al motor sobre el día del usuario.
 *
 * {@link #hoyCumplido} es la pregunta de siempre: todos los DIARIO activos
 * completados. Delega en {@link HabitoService#esDiaCompleto}, que ya conoce
 * esa regla. {@link #comoVaHoy} es la del ánimo en cuatro estados, y su
 * regla vive en {@link CalculoHoy#comoVa}.
 */
@Service
public class CumplimientoDiarioHabitos implements CumplimientoDiarioPort {

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Override
    public boolean hoyCumplido(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return false;
        return habitoService.esDiaCompleto(usuario);
    }

    @Override
    @Transactional
    public AvanceDeHoy comoVaHoy(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return AvanceDeHoy.SIN_EMPEZAR;
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        return comoVaHoy(usuario, LocalDate.now(zona), zona);
    }

    /** Igual, con el día de hoy dado, para que los tests lo fijen. */
    @Transactional
    public AvanceDeHoy comoVaHoy(Usuario usuario, LocalDate hoy, ZoneId zona) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);

        // Una sola consulta, desde el lunes de la semana pasada: es lo más
        // atrás que hace falta mirar para saber si lleva tiempo sin aparecer.
        Map<Integer, List<LocalDate>> fechasDeEstaSemana = new HashMap<>();
        List<LocalDate> actividadReciente = new ArrayList<>();
        for (Registro registro : registroDAO.findByHabitosAndRango(activos, lunes.minusWeeks(1), hoy)) {
            if (!registro.isCompletado()) continue;
            actividadReciente.add(registro.getFecha());
            if (!registro.getFecha().isBefore(lunes)) {
                fechasDeEstaSemana
                        .computeIfAbsent(registro.getHabito().getHabitoId(), id -> new ArrayList<>())
                        .add(registro.getFecha());
            }
        }

        // La referencia para quien no ha hecho nada en ese tiempo es el día
        // en que llegó: recién llegado no está ausente; quien llegó hace
        // mucho y no hace nada, sí. fechaRegistro se guarda en UTC.
        if (usuario.getFechaRegistro() != null) {
            actividadReciente.add(usuario.getFechaRegistro().atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(zona).toLocalDate());
        }

        return CalculoHoy.comoVa(activos, fechasDeEstaSemana, actividadReciente, hoy);
    }
}
