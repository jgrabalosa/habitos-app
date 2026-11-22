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
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Tendencia;
import com.norday.habitos.model.dto.SemanaPasadaDTO;
import com.norday.habitos.model.dto.SemanaPasadaDTO.MasSubeDTO;
import com.norday.habitos.model.dto.SemanaPasadaDTO.SemanalSemanaPasadaDTO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * El resumen de la semana pasada («Así fue tu semana»). Solo lectura: no
 * bloquea al usuario, no pone al día el objetivo de los hábitos, no mueve las
 * anclas del ritmo y no concede logros. Eso es lo que lo distingue de
 * {@link HoyService}, que se pide muchas veces al día y escribe.
 *
 * Los hábitos son los activos de ahora, como en Hoy: uno en pausa no cuenta
 * (ni esa semana ni hoy). Cada día se juzga con el objetivo que el hábito
 * tenía ese día ({@link HistorialObjetivos#vistaEl}); por eso no hace falta
 * poner al día la copia del objetivo que guarda el hábito.
 *
 * El hábito que más sube: se lee el ritmo de cada hábito con "hoy" puesto en
 * el lunes de esta semana. Ese ritmo ya cuenta la semana pasada entera y su
 * «hace siete días» es el del lunes anterior, así que la comparación es
 * exactamente la de la semana pasada aunque la app se abra el miércoles. Sube
 * el que cambia de nivel hacia arriba; de ellos, el que más sube en valor.
 * Un hábito creado esta semana no existía la semana pasada y no entra.
 */
@Service
public class SemanaPasadaService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private RitmoService ritmoService;

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Transactional(readOnly = true)
    public SemanaPasadaDTO obtener(Usuario usuario) {
        return obtener(usuario, Instant.now());
    }

    /**
     * Igual, en un instante dado: el "hoy" es el de ese instante en la zona
     * del usuario. Para que los tests fijen el reloj y puedan probar la zona.
     */
    @Transactional(readOnly = true)
    public SemanaPasadaDTO obtener(Usuario usuario, Instant ahora) {
        return obtener(usuario, ahora.atZone(zonaUsuarioService.zonaDe(usuario)).toLocalDate());
    }

    /** Igual, con el día de hoy dado, para que los tests lo fijen. */
    @Transactional(readOnly = true)
    public SemanaPasadaDTO obtener(Usuario usuario, LocalDate hoy) {
        LocalDate lunesActual = hoy.with(DayOfWeek.MONDAY);
        LocalDate lunes = lunesActual.minusWeeks(1);
        LocalDate domingo = lunesActual.minusDays(1);

        List<Habito> activos = habitoDAO.findActivos(usuario);
        Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(activos);
        List<Registro> registros = registroDAO.findByHabitosAndRango(activos, lunes, domingo);
        Map<Integer, Hechos> hechosPorHabito = Hechos.porHabito(registros, activos, historiales);

        boolean hayActividad = registros.stream().anyMatch(Registro::isCompletado);

        // Como el avance de Hoy, de los siete días: por cada día, lo que tocaba
        // en la sección del día y lo hecho; el día se completa si todo está hecho.
        int hechos = 0;
        int total = 0;
        int diasCompletos = 0;
        for (LocalDate dia = lunes; !dia.isAfter(domingo); dia = dia.plusDays(1)) {
            int tocaban = 0;
            int hechosDelDia = 0;
            for (Habito activo : activos) {
                Habito habito = HistorialObjetivos.de(historiales, activo).vistaEl(dia);
                if (!CalculoHoy.tocaEl(habito, dia)) continue;
                tocaban++;
                if (CalculoHoy.hechoEnElDia(habito, hechosPorHabito.get(activo.getHabitoId()), dia)) {
                    hechosDelDia++;
                }
            }
            total += tocaban;
            hechos += hechosDelDia;
            if (tocaban > 0 && hechosDelDia == tocaban) diasCompletos++;
        }

        List<SemanalSemanaPasadaDTO> semanales = new ArrayList<>();
        for (Habito activo : activos) {
            Habito habito = HistorialObjetivos.de(historiales, activo).vistaEl(domingo);
            if (habito.getFrecuencia() != Frecuencia.SEMANAL) continue;
            if (habito.getFechaInicio() != null && habito.getFechaInicio().isAfter(domingo)) continue;
            Hechos suyos = hechosPorHabito.get(activo.getHabitoId());
            semanales.add(new SemanalSemanaPasadaDTO(activo.getHabitoId(), habito.getNombre(),
                    habito.getIcono(), suyos.unidades(), habito.getMeta(),
                    CalculoHoy.hechoEnLaSemana(habito, suyos)));
        }

        // El ritmo de hoy, como en Hoy; y el del lunes de esta semana, para
        // el que más sube. Los dos sin escribir.
        Map<Integer, RitmoLeido> deHoy = ritmoService.ritmosLeidosSinEscribir(activos, hoy);
        Optional<RitmoLeido> general = ritmoService.generalLeido(activos, deHoy, hoy);
        Integer nivelGeneral = general.map(leido -> CalculoRitmo.nivel(leido.valor())).orElse(null);
        Tendencia tendenciaGeneral = general.map(RitmoLeido::tendencia).orElse(null);

        Map<Integer, RitmoLeido> alAcabarLaSemana = hoy.equals(lunesActual)
                ? deHoy
                : ritmoService.ritmosLeidosSinEscribir(activos, lunesActual);

        return new SemanaPasadaDTO(lunes.toString(), domingo.toString(), hayActividad,
                hechos, total, diasCompletos, semanales, nivelGeneral, tendenciaGeneral,
                masSube(activos, alAcabarLaSemana, domingo));
    }

    private MasSubeDTO masSube(List<Habito> activos, Map<Integer, RitmoLeido> leidos, LocalDate domingo) {
        MasSubeDTO mejor = null;
        double mayorSubida = 0.0;
        for (Habito habito : activos) {
            if (habito.getFechaInicio() != null && habito.getFechaInicio().isAfter(domingo)) continue;
            RitmoLeido leido = leidos.get(habito.getHabitoId());
            int antes = CalculoRitmo.nivel(leido.haceUnaSemana());
            int ahora = CalculoRitmo.nivel(leido.valor());
            double subida = leido.valor() - leido.haceUnaSemana();
            if (ahora > antes && subida > mayorSubida) {
                mejor = new MasSubeDTO(habito.getHabitoId(), habito.getNombre(), antes, ahora);
                mayorSubida = subida;
            }
        }
        return mejor;
    }
}
