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
import java.util.Collections;
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
        return avances(usuario, hoy, 1, hoy, zona).get(0);
    }

    @Override
    @Transactional
    public List<AvanceDeHoy> comoVaLosProximosDias(int usuarioId, int dias) {
        if (dias <= 0) return List.of();
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return Collections.nCopies(dias, AvanceDeHoy.SIN_EMPEZAR);
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        return comoVaLosProximosDias(usuario, LocalDate.now(zona), zona, dias);
    }

    /**
     * Igual, con el día de hoy dado: los {@code dias} días que siguen a
     * {@code hoy}, suponiendo que no se hace nada más. Cada uno es lo que
     * {@link #comoVaHoy(Usuario, LocalDate, ZoneId)} daría ese día con los
     * mismos registros: es la misma cuenta, con otro día.
     */
    @Transactional
    public List<AvanceDeHoy> comoVaLosProximosDias(Usuario usuario, LocalDate hoy, ZoneId zona, int dias) {
        if (dias <= 0) return List.of();
        return avances(usuario, hoy.plusDays(1), dias, hoy, zona);
    }

    /**
     * La regla de hoy para {@code cuantos} días seguidos desde {@code primero},
     * con lo hecho hasta {@code ultimoDato} (el último día con datos, que es
     * hoy de verdad). Las lecturas se hacen una vez para todos los días; lo
     * que cambia de un día a otro se saca de lo leído: qué registros son de
     * la semana de ese día y de la anterior, y qué objetivo rige.
     */
    private List<AvanceDeHoy> avances(Usuario usuario, LocalDate primero, int cuantos,
                                      LocalDate ultimoDato, ZoneId zona) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        // Con hábitos, pero todos en pausa: no es una ausencia, es una pausa.
        // Nori se queda dormida, no triste.
        if (activos.isEmpty() && !habitoDAO.findByPropietario(usuario).isEmpty()) {
            return Collections.nCopies(cuantos, AvanceDeHoy.SIN_EMPEZAR);
        }

        // Una sola consulta, desde el lunes de la semana pasada del primer
        // día: es lo más atrás que hace falta mirar para saber si lleva
        // tiempo sin aparecer. Para eso vale cualquier registro, también un
        // paso de minutos que no llega al objetivo: es actividad.
        List<Registro> completados = new ArrayList<>();
        LocalDate desde = primero.with(DayOfWeek.MONDAY).minusWeeks(1);
        for (Registro registro : registroDAO.findByHabitosAndRango(activos, desde, ultimoDato)) {
            if (registro.isCompletado()) completados.add(registro);
        }

        // La referencia para quien no ha hecho nada en ese tiempo es el día
        // en que llegó: recién llegado no está ausente; quien llegó hace
        // mucho y no hace nada, sí. fechaRegistro se guarda en UTC.
        LocalDate alta = usuario.getFechaRegistro() == null ? null
                : usuario.getFechaRegistro().atZone(ZoneOffset.UTC).withZoneSameInstant(zona).toLocalDate();

        Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(activos);
        List<AvanceDeHoy> avances = new ArrayList<>();
        for (int i = 0; i < cuantos; i++) {
            avances.add(avanceEl(primero.plusDays(i), activos, completados, alta, historiales));
        }
        return avances;
    }

    /** La regla de hoy para un día, con lo ya leído ({@code completados} puede traer días de más: se filtra). */
    private AvanceDeHoy avanceEl(LocalDate dia, List<Habito> activos, List<Registro> completados,
                                 LocalDate alta, Map<Integer, HistorialObjetivos> historiales) {
        LocalDate lunes = dia.with(DayOfWeek.MONDAY);
        LocalDate desde = lunes.minusWeeks(1);

        List<Registro> deEsaSemana = new ArrayList<>();
        List<LocalDate> actividadReciente = new ArrayList<>();
        for (Registro registro : completados) {
            LocalDate fecha = registro.getFecha();
            if (fecha.isBefore(desde) || fecha.isAfter(dia)) continue;
            actividadReciente.add(fecha);
            if (!fecha.isBefore(lunes)) deEsaSemana.add(registro);
        }
        if (alta != null) actividadReciente.add(alta);

        // Cada hábito con el objetivo vigente ese día, sin escribir nada, y
        // lo hecho esa semana con el objetivo de cada día.
        List<Habito> deEseDia = activos.stream()
                .map(h -> HistorialObjetivos.de(historiales, h).vistaEl(dia))
                .toList();
        Map<Integer, Hechos> hechos = Hechos.porHabito(deEsaSemana, activos, historiales);
        return CalculoHoy.comoVaConHechos(deEseDia, hechos, actividadReciente, dia);
    }
}
