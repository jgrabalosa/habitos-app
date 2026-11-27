package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.Tendencia;
import com.norday.habitos.model.Unidad;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Arma la pantalla de Hoy de la app nueva: las dos secciones, la tira de la
 * semana, el avance del día y los niveles de ritmo. Las reglas viven en
 * {@link CalculoHoy} y {@link CalculoRitmo}; aquí solo se leen los datos y
 * se reparten.
 *
 * Cuatro consultas sea cual sea el número de hábitos: los hábitos activos,
 * sus historiales de objetivos, los registros de la semana y —dentro de
 * RitmoService— las anclas y los registros que haga falta repasar.
 *
 * Cada día se juzga con el objetivo que el hábito tenía ese día: la fecha
 * mirada y cada día de la tira pueden caer antes de un cambio de objetivo.
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

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private HabitoService habitoService;

    /** Hoy tal como lo ve el usuario; con {@code fechaPedida}, otro día de la tira. */
    @Transactional
    public HoyDTO obtenerHoy(Usuario usuario, LocalDate fechaPedida) {
        LocalDate hoy = LocalDate.now(zonaUsuarioService.zonaDe(usuario));
        return obtenerHoy(usuario, fechaPedida != null ? fechaPedida : hoy, hoy);
    }

    /** Igual, con el día de hoy dado, para que los tests lo fijen. */
    @Transactional
    public HoyDTO obtenerHoy(Usuario usuario, LocalDate fecha, LocalDate hoy) {
        // Abrir Hoy concede los logros de ritmo (con sus monedas). El usuario
        // se bloquea lo primero, antes de las escrituras de la lectura (poner
        // al día el objetivo escribe en los hábitos, el ritmo en sus anclas):
        // así el orden sigue siendo usuario y después lo demás, y lo concedido
        // no se cuela en un completado a medias (ver bloquearUsuario).
        habitoService.bloquearUsuario(usuario);
        LocalDate lunes = fecha.with(DayOfWeek.MONDAY);
        List<Habito> activos = habitoDAO.findActivos(usuario);
        // Antes que el ritmo: si ha llegado un cambio programado, se aplica
        // y ancla el ritmo en su fecha.
        Map<Integer, HistorialObjetivos> historiales = objetivoService.ponerAlDia(activos, hoy);

        // Lo hecho de cada hábito en la semana, cada día con su objetivo: en
        // uno de minutos, un día cuenta cuando llega a sus minutos.
        Map<Integer, Hechos> hechosPorHabito = Hechos.porHabito(
                registroDAO.findByHabitosAndRango(activos, lunes, lunes.plusDays(6)), activos, historiales);

        // El ritmo es siempre el de hoy, aunque se mire otro día de la tira.
        Map<Integer, RitmoLeido> ritmos = ritmoService.ritmosLeidos(activos, hoy);

        List<FilaHoyDTO> delDia = new ArrayList<>();
        List<FilaHoyDTO> deLaSemana = new ArrayList<>();
        for (Habito activo : activos) {
            Habito habito = HistorialObjetivos.de(historiales, activo).vistaEl(fecha);
            Hechos hechos = hechosPorHabito.get(habito.getHabitoId());
            int nivel = CalculoRitmo.nivel(ritmos.get(habito.getHabitoId()).valor());

            // completados va en la unidad de la meta: veces en un hábito de
            // veces, días hechos en uno de minutos (0 o 1 en un diario).
            if (CalculoHoy.tocaEl(habito, fecha)) {
                int completados = habito.getFrecuencia() == Frecuencia.DIARIO
                        ? hechos.unidadesEl(fecha)
                        : hechos.unidades();
                delDia.add(fila(habito, completados,
                        CalculoHoy.hechoEnElDia(habito, hechos, fecha), hechos, fecha, nivel));
            } else if (CalculoHoy.vaEnLaSemanaDe(habito, fecha)) {
                deLaSemana.add(fila(habito, hechos.unidades(),
                        CalculoHoy.hechoEnLaSemana(habito, hechos), hechos, fecha, nivel));
            }
        }

        List<DiaTiraDTO> dias = new ArrayList<>();
        for (int i = 0; i < 7; i++) {
            LocalDate dia = lunes.plusDays(i);
            Double avance = dia.isAfter(hoy)
                    ? null
                    : oNull(CalculoHoy.avanceDelDiaConHechos(vistas(activos, historiales, dia), hechosPorHabito, dia));
            dias.add(new DiaTiraDTO(dia.toString(), avance));
        }

        Optional<RitmoLeido> general = ritmoService.generalLeido(activos, ritmos, hoy);
        Integer nivelGeneral = general.map(leido -> CalculoRitmo.nivel(leido.valor())).orElse(null);
        Tendencia tendenciaGeneral = general.map(RitmoLeido::tendencia).orElse(null);

        // Los logros de ritmo se conceden aquí, al leer: el ritmo se mueve al
        // cerrar el día o la semana y no al marcar, así que este es el momento
        // en que el usuario ve que ha subido. Al servicio de logros solo se le
        // pregunta si alguna condición se cumple, para que una lectura normal
        // no haga consultas de más.
        List<String> logrosCumplidos = CalculoRitmo.logrosDeRitmo(ritmos.values(), general);
        List<String> logrosOtorgados = logrosCumplidos.isEmpty()
                ? List.of()
                : logrosHabitosService.evaluarLogrosDeRitmo(usuario, logrosCumplidos);

        return new HoyDTO(fecha.toString(), hoy.toString(), dias,
                oNull(CalculoHoy.avanceDelDiaConHechos(vistas(activos, historiales, fecha), hechosPorHabito, fecha)),
                nivelGeneral, tendenciaGeneral, seccion(delDia), seccion(deLaSemana),
                logrosOtorgados);
    }

    private FilaHoyDTO fila(Habito habito, int completados, boolean hecho,
                            Hechos hechos, LocalDate fecha, int nivel) {
        boolean enMinutos = habito.getUnidad() == Unidad.MINUTOS;
        return new FilaHoyDTO(HabitoDTO.desde(habito), completados, habito.getMeta(), hecho,
                hechos.unidadesEl(fecha) > 0, nivel,
                hechos.fechasEnUnidades().stream().map(LocalDate::toString).toList(),
                habito.getUnidad(),
                enMinutos ? hechos.cantidadEl(fecha) : null,
                enMinutos ? habito.getMinutosObjetivo() : null);
    }

    /** Los hábitos tal como eran ese día (ver {@link HistorialObjetivos#vistaEl}). */
    private List<Habito> vistas(List<Habito> habitos, Map<Integer, HistorialObjetivos> historiales,
                                LocalDate dia) {
        return habitos.stream().map(h -> HistorialObjetivos.de(historiales, h).vistaEl(dia)).toList();
    }

    private SeccionHoyDTO seccion(List<FilaHoyDTO> filas) {
        int hechos = (int) filas.stream().filter(FilaHoyDTO::hecho).count();
        return new SeccionHoyDTO(hechos, filas.size(), filas);
    }

    private Double oNull(OptionalDouble valor) {
        return valor.isPresent() ? valor.getAsDouble() : null;
    }
}
