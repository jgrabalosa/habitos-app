package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoAlDia;
import com.norday.habitos.model.CalculoRitmo.RitmoConPeso;
import com.norday.habitos.model.CalculoRitmo.RitmoLeido;
import com.norday.habitos.model.Frecuencia;
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
import java.util.Optional;
import java.util.OptionalDouble;

/**
 * Única puerta de lectura del ritmo de un hábito.
 *
 * El ritmo no se guarda al día: se guarda un ancla (ver {@link Ritmo}) y aquí
 * se pone al día repasando los registros. No hay proceso nocturno. Completar
 * o deshacer un hábito no pasa por aquí ni escribe en la tabla: el cálculo
 * sale solo de los registros que haya.
 *
 * El ancla se mueve en dos momentos, y los dos se guardan. Al empezar una
 * semana nueva avanza al lunes: lo anterior ya no puede cambiar, así que no
 * tiene sentido repasarlo en cada lectura; se guarda también la que se deja
 * atrás, para calcular el ritmo de hace siete días. Y al cambiar la
 * frecuencia o el objetivo de un hábito pasa a ese día, para que lo ya
 * cerrado no se vuelva a juzgar con las reglas nuevas.
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
        return ritmoLeido(habito, hoy).valor();
    }

    /** El ritmo de hoy de un hábito y el de hace siete días. */
    @Transactional
    public RitmoLeido ritmoLeido(Habito habito, LocalDate hoy) {
        Ritmo ritmo = ritmoDAO.findByHabito(habito);
        if (ritmo == null) {
            return empezarHoy(habito, hoy);
        }
        if (!habito.isActivo()) {
            return congelado(ritmo);
        }
        return ponerAlDia(ritmo, habito, fechasHechas(ritmo, habito, hoy), hoy);
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
        ritmosLeidos(habitos, hoy).forEach((id, leido) -> vigentes.put(id, leido.valor()));
        return vigentes;
    }

    /** Igual que {@link #ritmosVigentes}, con el ritmo de hace siete días de cada uno. */
    @Transactional
    public Map<Integer, RitmoLeido> ritmosLeidos(List<Habito> habitos, LocalDate hoy) {
        Map<Integer, RitmoLeido> leidos = new HashMap<>();
        if (habitos.isEmpty()) return leidos;

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
            LocalDate inicio = desdeDondeLeer(ritmo, habito);
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
            RitmoLeido leido = ritmo == null
                    ? empezarHoy(habito, hoy)
                    : !habito.isActivo()
                            ? congelado(ritmo)
                            : ponerAlDia(ritmo, habito,
                                    fechasPorHabito.getOrDefault(habito.getHabitoId(), List.of()), hoy);
            leidos.put(habito.getHabitoId(), leido);
        }
        return leidos;
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
     * El ritmo general de hoy y el de hace siete días, a partir de ritmos ya
     * leídos con {@link #ritmosLeidos}. Vacío si no hay hábitos.
     *
     * El de hace siete días usa el peso que cada hábito tenía entonces: uno
     * creado esta semana no contaba, y así no distorsiona la comparación.
     */
    public Optional<RitmoLeido> generalLeido(List<Habito> habitos, Map<Integer, RitmoLeido> leidos,
                                             LocalDate hoy) {
        LocalDate haceUnaSemana = hoy.minusDays(7);
        List<RitmoConPeso> ahora = new ArrayList<>();
        List<RitmoConPeso> antes = new ArrayList<>();
        for (Habito habito : habitos) {
            RitmoLeido leido = leidos.get(habito.getHabitoId());
            ahora.add(new RitmoConPeso(leido.valor(),
                    CalculoRitmo.pesoEnElGeneral(habito.getFechaInicio(), hoy)));
            antes.add(new RitmoConPeso(leido.haceUnaSemana(),
                    CalculoRitmo.pesoEnElGeneral(habito.getFechaInicio(), haceUnaSemana)));
        }
        OptionalDouble generalAhora = CalculoRitmo.general(ahora);
        if (generalAhora.isEmpty()) return Optional.empty();
        return Optional.of(new RitmoLeido(
                generalAhora.getAsDouble(), CalculoRitmo.general(antes).getAsDouble()));
    }

    /**
     * Hay que llamarlo ANTES de cambiar la frecuencia o el objetivo de un
     * hábito, con el hábito tal como estaba.
     *
     * Deja el ancla en hoy con el ritmo que había, calculado con las reglas
     * antiguas: así el ritmo se conserva y lo ya cerrado no se vuelve a
     * juzgar con las nuevas. Y apunta hoy como día del cambio, con lo que el
     * periodo en curso solo podrá subir: un cambio nunca perjudica.
     *
     * A partir de aquí, marcar un día anterior al cambio ya no mueve el
     * ritmo: esos días quedaron cerrados.
     *
     * Un hábito en pausa no se toca.
     */
    @Transactional
    public void anclarAntesDeCambiar(Habito habitoComoEstaba) {
        anclarAntesDeCambiar(habitoComoEstaba,
                LocalDate.now(zonaUsuarioService.zonaDe(habitoComoEstaba.getPropietario())));
    }

    /** Igual, con el día dado, para que los tests lo fijen. */
    @Transactional
    public void anclarAntesDeCambiar(Habito habitoComoEstaba, LocalDate hoy) {
        // En pausa no se toca: el ritmo se quedó donde se pausó, y al
        // reanudar el ancla pasa a ese día y ya cuenta con las reglas nuevas.
        if (!habitoComoEstaba.isActivo()) {
            return;
        }
        Ritmo ritmo = ritmoDAO.findByHabito(habitoComoEstaba);
        if (ritmo == null) {
            ritmo = new Ritmo(habitoComoEstaba, hoy);
            ritmo.setCambio(hoy);
            ritmoDAO.save(ritmo);
            return;
        }
        double vigente = ponerAlDia(ritmo, habitoComoEstaba,
                fechasHechas(ritmo, habitoComoEstaba, hoy), hoy).valor();
        ritmo.setValor(vigente);
        ritmo.setDesde(hoy);
        ritmo.setCambio(hoy);
        ritmoDAO.update(ritmo);
    }

    /**
     * Hay que llamarlo al reanudar un hábito en pausa, antes de activarlo.
     *
     * El ancla pasa al día de la vuelta con el valor que tenía al pausar: los
     * días de pausa no se juzgan. La vuelta queda como día de cambio, así que
     * el periodo en curso solo puede subir. Se borra el ancla anterior para
     * que la tendencia no compare con lo de antes de la pausa.
     */
    @Transactional
    public void reanudar(Habito habito, LocalDate hoy) {
        Ritmo ritmo = ritmoDAO.findByHabito(habito);
        if (ritmo == null) {
            ritmo = new Ritmo(habito, hoy);
            ritmo.setCambio(hoy);
            ritmoDAO.save(ritmo);
            return;
        }
        ritmo.setDesde(hoy);
        ritmo.setCambio(hoy);
        ritmo.setValorPrevio(null);
        ritmo.setDesdePrevio(null);
        ritmoDAO.update(ritmo);
    }

    /**
     * Desde qué fecha hay que leer registros para poner al día un ritmo: el
     * inicio del periodo de su ancla más antigua. Un semanal cuenta la semana
     * entera, de ahí que sea el inicio del periodo y no el ancla misma.
     */
    private LocalDate desdeDondeLeer(Ritmo ritmo, Habito habito) {
        LocalDate ancla = ritmo.getDesdePrevio() != null && ritmo.getDesdePrevio().isBefore(ritmo.getDesde())
                ? ritmo.getDesdePrevio()
                : ritmo.getDesde();
        return habito.getFrecuencia().rangoPeriodo(ancla)[0];
    }

    /** Las fechas hechas de un hábito que hacen falta para ponerlo al día. Hoy no se lee: su periodo sigue abierto. */
    private List<LocalDate> fechasHechas(Ritmo ritmo, Habito habito, LocalDate hoy) {
        LocalDate inicio = desdeDondeLeer(ritmo, habito);
        if (!inicio.isBefore(hoy)) return List.of();
        return registroDAO.findByHabitoAndRango(habito, inicio, hoy.minusDays(1)).stream()
                .filter(Registro::isCompletado)
                .map(Registro::getFecha)
                .toList();
    }

    /**
     * Un hábito en pausa no se pone al día: su ritmo se queda donde se pausó
     * y no se guarda nada. Sin esto, abrir su detalle lo haría caer.
     */
    private RitmoLeido congelado(Ritmo ritmo) {
        return new RitmoLeido(ritmo.getValor(), ritmo.getValor());
    }

    /**
     * No debería pasar: el ritmo nace con el hábito y la migración se lo dio
     * a los que ya existían. Si falta, empieza hoy en el valor de partida.
     */
    private RitmoLeido empezarHoy(Habito habito, LocalDate hoy) {
        Ritmo ritmo = new Ritmo(habito, hoy);
        ritmoDAO.save(ritmo);
        return new RitmoLeido(ritmo.getValor(), ritmo.getValor());
    }

    /**
     * Pone al día un ancla y, si ha avanzado, la guarda junto con la que deja
     * atrás. Devuelve el ritmo de hoy y el de hace siete días.
     */
    private RitmoLeido ponerAlDia(Ritmo ritmo, Habito habito, List<LocalDate> fechasHechas, LocalDate hoy) {
        Frecuencia frecuencia = habito.getFrecuencia();
        int objetivo = habito.getMeta();
        // El periodo que solo puede subir: el del último cambio de frecuencia
        // u objetivo y, si no ha habido ninguno, el primero del hábito.
        LocalDate soloSube = ritmo.getCambio() != null ? ritmo.getCambio() : habito.getFechaInicio();

        RitmoAlDia alDia = CalculoRitmo.alDia(ritmo.getValor(), ritmo.getDesde(),
                frecuencia, objetivo, soloSube, fechasHechas, hoy);

        if (alDia.desdeAncla().isAfter(ritmo.getDesde())) {
            // El ancla anterior es el ritmo una semana antes de la nueva. Si
            // la que había es más reciente que eso —un hábito creado o
            // cambiado la semana pasada—, la anterior es esa misma.
            LocalDate unaSemanaAntes = alDia.desdeAncla().minusDays(7);
            if (ritmo.getDesde().isAfter(unaSemanaAntes)) {
                ritmo.setValorPrevio(ritmo.getValor());
                ritmo.setDesdePrevio(ritmo.getDesde());
            } else {
                ritmo.setValorPrevio(CalculoRitmo.alDia(ritmo.getValor(), ritmo.getDesde(),
                        frecuencia, objetivo, soloSube, fechasHechas, unaSemanaAntes).valor());
                ritmo.setDesdePrevio(unaSemanaAntes);
            }
            ritmo.setValor(alDia.valorAncla());
            ritmo.setDesde(alDia.desdeAncla());
            ritmoDAO.update(ritmo);
        }

        // El ritmo de hace siete días se calcula desde el ancla anterior; si
        // el ancla no ha avanzado nunca, desde la única que hay.
        boolean hayAnterior = ritmo.getDesdePrevio() != null;
        double haceUnaSemana = CalculoRitmo.alDia(
                hayAnterior ? ritmo.getValorPrevio() : ritmo.getValor(),
                hayAnterior ? ritmo.getDesdePrevio() : ritmo.getDesde(),
                frecuencia, objetivo, soloSube, fechasHechas, hoy.minusDays(7)).valor();

        return new RitmoLeido(alDia.valor(), haceUnaSemana);
    }
}
