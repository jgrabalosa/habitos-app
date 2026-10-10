package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoAlDia;
import com.norday.habitos.model.CalculoRitmo.RitmoConPeso;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;

/**
 * Única puerta de lectura del ritmo de un hábito.
 *
 * El ritmo no se guarda al día: se guarda un ancla (ver {@link Ritmo}) y aquí
 * se pone al día repasando los registros. No hay proceso nocturno. Completar
 * o deshacer un hábito no pasa por aquí ni escribe en la tabla: el cálculo
 * sale solo de los registros que haya.
 *
 * Lo único que se guarda es el avance del ancla al empezar una semana nueva,
 * y se guarda a propósito: lo anterior al lunes ya no puede cambiar, así que
 * no tiene sentido repasarlo en cada lectura.
 */
@Service
public class RitmoService {

    @Autowired
    private IRitmoDAO ritmoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /** El ritmo de hoy de un hábito, de 0 a 1, en el "hoy" de su dueño. */
    @Transactional
    public double ritmoVigente(Habito habito) {
        return ritmoVigente(habito, LocalDate.now(zonaUsuarioService.zonaDe(habito.getPropietario())));
    }

    /**
     * Igual, con el día dado. Existe para que los tests fijen el día: contra
     * el reloj real no se puede escribir un test de cambio de semana.
     */
    @Transactional
    public double ritmoVigente(Habito habito, LocalDate hoy) {
        Ritmo ritmo = ritmoDAO.findByHabito(habito);
        if (ritmo == null) {
            return empezarHoy(habito, hoy);
        }

        // Un semanal cuenta la semana entera, así que se lee desde el inicio
        // del periodo del ancla. Hoy no se lee: su periodo sigue abierto.
        LocalDate inicio = habito.getFrecuencia().rangoPeriodo(ritmo.getDesde())[0];
        List<LocalDate> fechasHechas = inicio.isBefore(hoy)
                ? registroDAO.findByHabitoAndRango(habito, inicio, hoy.minusDays(1)).stream()
                        .filter(Registro::isCompletado)
                        .map(Registro::getFecha)
                        .toList()
                : List.of();

        return ponerAlDia(ritmo, habito, fechasHechas, hoy);
    }

    /**
     * Los ritmos de hoy de varios hábitos, por id de hábito. Da lo mismo que
     * leerlos uno a uno, pero en dos consultas —las anclas y los registros—
     * sea cual sea el número de hábitos: es lo que usa la pantalla de Hoy,
     * que se carga en cada entrada a la app.
     */
    @Transactional
    public Map<Integer, Double> ritmosVigentes(List<Habito> habitos, LocalDate hoy) {
        Map<Integer, Double> vigentes = new HashMap<>();
        if (habitos.isEmpty()) return vigentes;

        Map<Integer, Ritmo> anclas = new HashMap<>();
        for (Ritmo ritmo : ritmoDAO.findByHabitos(habitos)) {
            anclas.put(ritmo.getHabito().getHabitoId(), ritmo);
        }

        // Una sola consulta de registros, desde el inicio de periodo más
        // antiguo de todas las anclas. A cada hábito le llegan también fechas
        // anteriores a su ancla; CalculoRitmo.alDia las ignora, porque
        // empieza a contar en el periodo de su ancla.
        LocalDate desde = null;
        for (Habito habito : habitos) {
            Ritmo ritmo = anclas.get(habito.getHabitoId());
            if (ritmo == null) continue;
            LocalDate inicio = habito.getFrecuencia().rangoPeriodo(ritmo.getDesde())[0];
            if (desde == null || inicio.isBefore(desde)) desde = inicio;
        }
        Map<Integer, List<LocalDate>> fechasPorHabito = new HashMap<>();
        if (desde != null && desde.isBefore(hoy)) {
            for (Registro registro : registroDAO.findByHabitosAndRango(habitos, desde, hoy.minusDays(1))) {
                if (!registro.isCompletado()) continue;
                fechasPorHabito
                        .computeIfAbsent(registro.getHabito().getHabitoId(), id -> new ArrayList<>())
                        .add(registro.getFecha());
            }
        }

        for (Habito habito : habitos) {
            Ritmo ritmo = anclas.get(habito.getHabitoId());
            double vigente = ritmo == null
                    ? empezarHoy(habito, hoy)
                    : ponerAlDia(ritmo, habito,
                            fechasPorHabito.getOrDefault(habito.getHabitoId(), List.of()), hoy);
            vigentes.put(habito.getHabitoId(), vigente);
        }
        return vigentes;
    }

    /**
     * El ritmo general de un usuario, de 0 a 1, en su "hoy": la media de los
     * ritmos de sus hábitos activos. Vacío si no tiene ninguno.
     */
    @Transactional
    public OptionalDouble ritmoGeneral(Usuario usuario) {
        return ritmoGeneral(usuario, LocalDate.now(zonaUsuarioService.zonaDe(usuario)));
    }

    /** Igual, con el día dado, para que los tests lo fijen. */
    @Transactional
    public OptionalDouble ritmoGeneral(Usuario usuario, LocalDate hoy) {
        List<Habito> activos = habitoDAO.findActivos(usuario);
        return generalDe(activos, ritmosVigentes(activos, hoy), hoy);
    }

    /**
     * El ritmo general de unos hábitos cuyos ritmos ya se han leído con
     * {@link #ritmosVigentes}. Para quien necesita los dos datos —el de cada
     * hábito y el general— sin leer dos veces.
     */
    public OptionalDouble generalDe(List<Habito> habitos, Map<Integer, Double> ritmos, LocalDate hoy) {
        List<RitmoConPeso> conPeso = new ArrayList<>();
        for (Habito habito : habitos) {
            conPeso.add(new RitmoConPeso(
                    ritmos.get(habito.getHabitoId()),
                    CalculoRitmo.pesoEnElGeneral(habito.getFechaInicio(), hoy)));
        }
        return CalculoRitmo.general(conPeso);
    }

    /**
     * No debería pasar: el ritmo nace con el hábito y la migración se lo dio
     * a los que ya existían. Si falta, empieza hoy en el valor de partida.
     */
    private double empezarHoy(Habito habito, LocalDate hoy) {
        Ritmo ritmo = new Ritmo(habito, hoy);
        ritmoDAO.save(ritmo);
        return ritmo.getValor();
    }

    /** Pone al día un ancla y, si ha avanzado, la guarda. Devuelve el ritmo de hoy. */
    private double ponerAlDia(Ritmo ritmo, Habito habito, List<LocalDate> fechasHechas, LocalDate hoy) {
        RitmoAlDia alDia = CalculoRitmo.alDia(ritmo.getValor(), ritmo.getDesde(),
                habito.getFrecuencia(), habito.getMeta(), habito.getFechaInicio(), fechasHechas, hoy);

        if (alDia.desdeAncla().isAfter(ritmo.getDesde())) {
            ritmo.setValor(alDia.valorAncla());
            ritmo.setDesde(alDia.desdeAncla());
            ritmoDAO.update(ritmo);
        }
        return alDia.valor();
    }
}
