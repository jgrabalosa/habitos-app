package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.gamificacion.service.CumplimientoDiarioPort;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.HistorialObjetivos;
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

    @Autowired
    private ObjetivoService objetivoService;

    @Override
    public boolean hoyCumplido(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return false;
        return habitoService.esDiaCompleto(usuario);
    }

    /**
     * Usa Norday Habits quien tiene algún hábito, activo o en pausa. Sin
     * ninguno, el ánimo de Nori lo deciden las otras apps que use
     * (CumplimientoCombinado).
     */
    @Override
    @Transactional
    public boolean enUso(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        return usuario != null && !habitoDAO.findByPropietario(usuario).isEmpty();
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
        // Con hábitos, pero todos en pausa: no es una ausencia, es una pausa.
        // Nori se queda dormida, no triste.
        if (activos.isEmpty() && !habitoDAO.findByPropietario(usuario).isEmpty()) {
            return AvanceDeHoy.SIN_EMPEZAR;
        }
        LocalDate lunes = hoy.with(DayOfWeek.MONDAY);

        // Una sola consulta, desde el lunes de la semana pasada: es lo más
        // atrás que hace falta mirar para saber si lleva tiempo sin aparecer.
        // Para eso vale cualquier registro, también un paso de minutos que no
        // llega al objetivo: es actividad.
        List<Registro> deEstaSemana = new ArrayList<>();
        List<LocalDate> actividadReciente = new ArrayList<>();
        for (Registro registro : registroDAO.findByHabitosAndRango(activos, lunes.minusWeeks(1), hoy)) {
            if (!registro.isCompletado()) continue;
            actividadReciente.add(registro.getFecha());
            if (!registro.getFecha().isBefore(lunes)) {
                deEstaSemana.add(registro);
            }
        }

        // La referencia para quien no ha hecho nada en ese tiempo es el día
        // en que llegó: recién llegado no está ausente; quien llegó hace
        // mucho y no hace nada, sí. fechaRegistro se guarda en UTC.
        if (usuario.getFechaRegistro() != null) {
            actividadReciente.add(usuario.getFechaRegistro().atZone(ZoneOffset.UTC)
                    .withZoneSameInstant(zona).toLocalDate());
        }

        // Cada hábito con el objetivo vigente hoy, sin escribir nada, y lo
        // hecho esta semana con el objetivo de cada día.
        Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(activos);
        List<Habito> deHoy = activos.stream()
                .map(h -> HistorialObjetivos.de(historiales, h).vistaEl(hoy))
                .toList();
        Map<Integer, Hechos> hechos = Hechos.porHabito(deEstaSemana, activos, historiales);
        return CalculoHoy.comoVaConHechos(deHoy, hechos, actividadReciente, hoy);
    }
}
