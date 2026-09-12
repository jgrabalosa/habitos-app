package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.ReversionLogro;
import com.norday.habitos.model.ReversionRegistro;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IReversionRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class RegistroService {

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

    @Autowired
    private RachaService rachaService;

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private ILogroDAO logroDAO;

    @Autowired
    private IUsuarioLogroDAO usuarioLogroDAO;

    @Autowired
    private IReversionRegistroDAO reversionRegistroDAO;

    // Un día de compromiso cumplido vale igual sea DIARIO o SEMANAL, meta 1 o meta 4:
    // el valor está en el compromiso diario, no en cómo esté configurado el hábito.
    private static final int PUNTOS_POR_DIA_COMPLETADO = 50;
    /** Umbral de racha y puntos que paga. Ordenados de menor a mayor. */
    private static final int[][] HITOS_RACHA = {
            {3, 50}, {7, 100}, {30, 300}, {100, 750}, {365, 2000}
    };
    private static final int XP_POR_DIA_COMPLETADO = 5;

    @Transactional
    public Map<String, Object> completarHabito(Habito habito, String nota) {
        return completarHabito(habito, nota, null);
    }

    @Transactional
    public Map<String, Object> completarHabito(Habito habito, String nota, LocalDate fechaSolicitada) {
        LocalDate hoy = rachaService.hoyDe(habito);
        LocalDate fecha = fechaSolicitada != null ? fechaSolicitada : hoy;
        if (fecha.isAfter(hoy)) {
            throw new ConflictoException("No se puede completar un hábito en una fecha futura");
        }
        // Suelo del completado retroactivo: el lunes de la semana en curso.
        // Sin límite, rellenar meses enteros dispara puntos, XP y logros de
        // racha a voluntad. El lunes se calcula explícito porque
        // rangoPeriodo(hoy) devuelve el propio día en DIARIO.
        LocalDate lunesDeEstaSemana = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        if (fecha.isBefore(lunesDeEstaSemana)) {
            throw new ConflictoException("Solo se puede completar una fecha de la semana en curso");
        }
        Usuario usuario = habito.getPropietario();

        // SEMANAL: máximo un completado por día (cada completado es un día distinto)
        if (habito.getFrecuencia() == Frecuencia.SEMANAL
                && registroDAO.existeRegistroEnFecha(habito, fecha)) {
            throw new ConflictoException("Este hábito ya se ha completado en esa fecha");
        }

        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodo(fecha);
        int completadosAntes = registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
        int meta = habito.getMeta();

        // DIARIO: no dejar superar la meta del día. Sin esto, un doble toque o
        // un reintento por timeout de red crea un registro de más y, si
        // coincide justo con el punto en que se alcanza la meta, otorga
        // puntos por partida doble.
        if (habito.getFrecuencia() == Frecuencia.DIARIO && completadosAntes >= meta) {
            throw new ConflictoException("Este hábito ya ha alcanzado la meta de esa fecha");
        }

        // Instantánea del estado previo, para poder deshacer este completado con
        // exactitud (ver ReversionRegistro). Valores primitivos copiados a
        // propósito: si guardásemos la entidad, Hibernate la modificaría más
        // abajo y aquí leeríamos ya los valores nuevos.
        int saldoAntes = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId());
        Racha rachaPrevia = rachaDAO.findByHabito(habito);
        Integer rachaActualPrevia = rachaPrevia != null ? rachaPrevia.getRachaActual() : null;
        Integer rachaMaximaPrevia = rachaPrevia != null ? rachaPrevia.getRachaMaxima() : null;
        LocalDate periodoMetaAlcanzadaPrevio = rachaPrevia != null ? rachaPrevia.getPeriodoMetaAlcanzada() : null;
        LocalDate ultimaFechaPrevia = rachaPrevia != null ? rachaPrevia.getUltimaFecha() : null;
        Mascota mascotaPrevia = mascotaService.obtenerOCrear(usuario.getUsuarioId());
        int mascotaExperienciaPrevia = mascotaPrevia.getExperiencia();
        LocalDate mascotaDiaCompletoPrevio = mascotaPrevia.getFechaUltimoDiaCompleto();
        Set<Integer> logrosPrevios = idsLogros(usuario);

        Registro registro = new Registro(habito, true, nota, fecha);
        registroDAO.save(registro);

        int puntosGanados = 0;
        boolean subioNivel = false;
        int nivelNuevo = 0;

        // Puntos y XP solo en el instante exacto en que se alcanza la meta del día —
        // ni antes, ni de nuevo si sigues completando después de alcanzarla
        if (completadosAntes + 1 == meta) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, PUNTOS_POR_DIA_COMPLETADO, "HABITO_COMPLETADO",
                    habito.getHabitoId(), "Hábito completado: " + habito.getNombre()
            );
            puntosGanados += PUNTOS_POR_DIA_COMPLETADO;

            ResultadoExperienciaDTO resultadoXp =
                    mascotaService.ganarExperiencia(usuario.getUsuarioId(), XP_POR_DIA_COMPLETADO);
            subioNivel = resultadoXp.isSubioNivel();
            nivelNuevo = resultadoXp.getNivelNuevo();
        }

        boolean metaAlcanzadaAhora = actualizarRacha(habito, completadosAntes + 1, meta, hoy, fecha);
        if (metaAlcanzadaAhora) {
            puntosGanados += otorgarPuntosPorHitoRacha(usuario, habito, rachaActualPrevia);
        }

        // Con este registro puede haberse cerrado el día entero. Va después de
        // guardar el Registro a propósito: la consulta de esDiaCompleto es JPQL,
        // así que Hibernate hace flush antes y el registro recién creado cuenta.
        if (fecha.equals(hoy) && habitoService.esDiaCompleto(usuario)) {
            mascotaService.registrarDiaCompleto(usuario.getUsuarioId());
        }

        // Los logros de racha leen rachaActual/rachaMaxima en crudo, pero aquí
        // ya es seguro: actualizarRacha acaba de normalizarla en esta misma
        // llamada, así que no puede haber valores rancios.
        List<String> logros = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        // Cuándo mostrar el sheet de valoración: SEMANAL siempre (cada completado es
        // un día distinto), DIARIO solo en el último completado del día (al llegar a la meta)
        boolean mostrarValoracion = fecha.equals(hoy)
                && (habito.getFrecuencia() == Frecuencia.SEMANAL
                || (completadosAntes + 1) >= meta);

        // Cierra la instantánea con lo que efectivamente ocurrió: el delta de
        // saldo recoge de una vez el completado, el hito de racha y los logros.
        int monedasOtorgadas = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId()) - saldoAntes;
        ReversionRegistro reversion = new ReversionRegistro(registro, rachaActualPrevia, rachaMaximaPrevia,
                periodoMetaAlcanzadaPrevio, ultimaFechaPrevia, mascotaExperienciaPrevia,
                mascotaDiaCompletoPrevio, monedasOtorgadas);
        for (String codigo : logros) {
            Logro logro = logroDAO.findByCodigo(codigo);
            if (logro != null) {
                reversion.getLogros().add(new ReversionLogro(reversion, logro.getLogroId()));
            }
        }
        // También captura logros disparados indirectamente por la mascota
        // (por ejemplo, al cambiar de fase al ganar XP), que no forman parte
        // de la lista devuelta por LogrosHabitosService.
        for (Integer logroId : idsLogros(usuario)) {
            if (!logrosPrevios.contains(logroId)
                    && reversion.getLogros().stream().noneMatch(r -> r.getLogroRef() == logroId)) {
                reversion.getLogros().add(new ReversionLogro(reversion, logroId));
            }
        }
        reversionRegistroDAO.save(reversion);

        return Map.of(
                "logros", logros,
                "puntosGanados", puntosGanados,
                "registroId", registro.getRegistroId(),
                "mostrarValoracion", mostrarValoracion,
                "subioNivel", subioNivel,
                "nivelNuevo", nivelNuevo
        );
    }

    private Set<Integer> idsLogros(Usuario usuario) {
        Set<Integer> ids = new HashSet<>();
        usuarioLogroDAO.findByUsuario(usuario).forEach(ul -> {
            if (ul.getLogro() != null) ids.add(ul.getLogro().getLogroId());
        });
        return ids;
    }

    public int contarCompletadosPeriodoActual(Habito habito) {
        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodo(rachaService.hoyDe(habito));
        return registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
    }

    /**
     * Actualiza la racha SOLO si se alcanza la meta del periodo por primera vez en ese periodo.
     * Devuelve true si la racha acaba de subir en esta llamada (para disparar puntos de hito).
     */
    private boolean actualizarRacha(Habito habito, int completadosEnPeriodo, int meta,
                                    LocalDate hoy, LocalDate fecha) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return false;

        int actualAntes = racha.getRachaActual();
        LocalDate[] periodoObjetivo = habito.getFrecuencia().rangoPeriodo(fecha);

        if (racha.getPeriodoMetaAlcanzada() != null
                && racha.getPeriodoMetaAlcanzada().equals(periodoObjetivo[0])) {
            return false; // ya subió este periodo, completar de más no hace nada
        }

        if (completadosEnPeriodo >= meta) {
            if (!fecha.equals(hoy)) {
                return recalcularRachaTrasFechaPasada(habito, racha, meta, hoy, actualAntes);
            }
            racha.setRachaActual(actualAntes + 1);
            if (racha.getRachaActual() > racha.getRachaMaxima()) racha.setRachaMaxima(racha.getRachaActual());
            racha.setPeriodoMetaAlcanzada(periodoObjetivo[0]);
            racha.setUltimaFecha(fecha);
            rachaDAO.update(racha);
            return true;
        }

        return false;
    }

    /**
     * Una fecha pasada puede rellenar un hueco y conectar dos tramos. En ese
     * caso no basta con sumar uno al valor materializado: se reconstruyen las
     * rachas de periodos a partir de los registros existentes. El snapshot de
     * ReversionRegistro conserva el estado anterior para poder deshacerlo.
     */
    private boolean recalcularRachaTrasFechaPasada(Habito habito, Racha racha, int meta,
                                                   LocalDate hoy, int actualAntes) {
        Map<LocalDate, Integer> completadosPorPeriodo = new HashMap<>();
        for (Registro registro : registroDAO.findByHabito(habito)) {
            if (!registro.isCompletado()) continue;
            LocalDate inicio = habito.getFrecuencia().rangoPeriodo(registro.getFecha())[0];
            completadosPorPeriodo.merge(inicio, 1, Integer::sum);
        }

        Set<LocalDate> cumplidos = new HashSet<>();
        for (Map.Entry<LocalDate, Integer> entrada : completadosPorPeriodo.entrySet()) {
            if (entrada.getValue() >= meta) cumplidos.add(entrada.getKey());
        }
        if (cumplidos.isEmpty()) return false;

        LocalDate ultimoPeriodo = cumplidos.stream().max(Comparator.naturalOrder()).orElseThrow();
        LocalDate periodoAnterior = habito.getFrecuencia().inicioPeriodoAnterior(hoy);
        int actual = 0;
        if (!ultimoPeriodo.isBefore(periodoAnterior)) {
            LocalDate cursor = ultimoPeriodo;
            while (cumplidos.contains(cursor)) {
                actual++;
                cursor = habito.getFrecuencia() == Frecuencia.SEMANAL
                        ? cursor.minusWeeks(1) : cursor.minusDays(1);
            }
        }

        int maxima = 0;
        int tramo = 0;
        LocalDate cursor = cumplidos.stream().min(Comparator.naturalOrder()).orElseThrow();
        LocalDate limite = ultimoPeriodo;
        while (!cursor.isAfter(limite)) {
            if (cumplidos.contains(cursor)) {
                tramo++;
                maxima = Math.max(maxima, tramo);
            } else {
                tramo = 0;
            }
            cursor = habito.getFrecuencia() == Frecuencia.SEMANAL
                    ? cursor.plusWeeks(1) : cursor.plusDays(1);
        }

        racha.setRachaActual(actual);
        racha.setRachaMaxima(Math.max(racha.getRachaMaxima(), maxima));
        racha.setPeriodoMetaAlcanzada(ultimoPeriodo);
        List<Registro> registros = registroDAO.findByHabito(habito);
        registros.stream()
                .filter(r -> habito.getFrecuencia().rangoPeriodo(r.getFecha())[0].equals(ultimoPeriodo))
                .max(Comparator.comparing(Registro::getFecha).thenComparing(Registro::getRegistroId))
                .ifPresent(r -> racha.setUltimaFecha(r.getFecha()));
        rachaDAO.update(racha);
        return actual > actualAntes;
    }

    /**
     * Paga todos los hitos que la racha ha cruzado en este completado, no solo
     * el del valor exacto. Con el completado retroactivo la racha puede saltar
     * —rellenar un hueco que une dos tramos lleva de 2 a 5— y con un switch
     * sobre el valor exacto esos hitos intermedios no se cobraban nunca.
     *
     * En el camino normal la racha sube de uno en uno, así que el intervalo
     * (previa, actual] contiene un único valor y el comportamiento es idéntico
     * al de antes. El límite de la semana en curso acota el salto máximo.
     *
     * Los hitos siguen siendo recobrables: si la racha se rompe y se reconstruye,
     * se vuelven a pagar. Eso ya era así y no se cambia aquí.
     */
    private int otorgarPuntosPorHitoRacha(Usuario usuario, Habito habito, Integer rachaPrevia) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return 0;

        int actual = racha.getRachaActual();
        int desde = rachaPrevia != null ? rachaPrevia : 0;
        if (actual <= desde) return 0;

        int puntos = 0;
        for (int[] hito : HITOS_RACHA) {
            if (hito[0] > desde && hito[0] <= actual) {
                puntos += hito[1];
            }
        }

        if (puntos > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, puntos, "HITO_RACHA", habito.getHabitoId(),
                    "Hito de racha (" + actual + ") en: " + habito.getNombre()
            );
        }
        return puntos;
    }

    public boolean estaCompletadoHoy(Habito habito) {
        return registroDAO.existeRegistroEnFecha(habito, rachaService.hoyDe(habito));
    }

    public List<Registro> obtenerRegistros(Habito habito) {
        return registroDAO.findByHabito(habito);
    }

    public Registro buscarPorId(int registroId) {
        return registroDAO.findById(registroId);
    }

    public void actualizarNota(int registroId, String nota) {
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RecursoNoEncontradoException("Registro no encontrado");
        }
        registro.setNota(nota);
        registroDAO.update(registro);

        Usuario usuario = registro.getHabito().getPropietario();
        logrosHabitosService.evaluarTrasAnadirNota(usuario);
    }

    public void actualizarValoracion(int registroId, Integer valoracion) {
        if (valoracion == null || valoracion < 1 || valoracion > 5) {
            throw new IllegalArgumentException("La valoración debe estar entre 1 y 5");
        }
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RecursoNoEncontradoException("Registro no encontrado");
        }
        registro.setValoracion(valoracion);
        registroDAO.update(registro);
    }

    /**
     * Deshace un hábito completado, revirtiendo con exactitud a partir de la
     * instantánea capturada al completarlo. Se mantiene la restricción de que
     * debe ser el último registro del hábito: así una reversión nunca pisa los
     * efectos de acciones posteriores independientes. La fecha puede ser hoy
     * o cualquier fecha pasada.
     */
    @Transactional
    public Map<String, Object> deshacerRegistro(int registroId) {
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RecursoNoEncontradoException("Registro no encontrado");
        }
        Habito habito = registro.getHabito();
        Usuario usuario = habito.getPropietario();

        // Solo se puede deshacer el último registro del hábito.
        List<Registro> registrosHabito = registroDAO.findByHabito(habito);
        Registro ultimo = null;
        for (Registro r : registrosHabito) {
            if (ultimo == null || r.getRegistroId() > ultimo.getRegistroId()) {
                ultimo = r;
            }
        }
        if (ultimo == null || ultimo.getRegistroId() != registroId) {
            throw new ConflictoException("Solo se puede deshacer el último completado");
        }

        // Mismo suelo que el completado retroactivo: la semana en curso. Sin
        // esto se puede deshacer un registro de hace meses y romper la racha
        // hacia atrás, que es justo lo que el límite de completar protege. Las
        // dos puertas al mismo sitio: lo que no se puede marcar, tampoco se
        // desmarca.
        LocalDate lunesDeEstaSemana = rachaService.hoyDe(habito)
                .minusDays(rachaService.hoyDe(habito).getDayOfWeek().getValue() - 1L);
        if (registro.getFecha().isBefore(lunesDeEstaSemana)) {
            throw new ConflictoException("Solo se puede deshacer un completado de la semana en curso");
        }

        ReversionRegistro reversion = reversionRegistroDAO.findByRegistro(registroId);
        if (reversion == null) {
            throw new ConflictoException(
                    "Este completado es anterior al sistema de deshacer y no se puede revertir");
        }

        // Retira los logros para que puedan reconcederse. Sus monedas ya van
        // dentro del delta que se compensa a continuación.
        List<Integer> logrosRetirados = new ArrayList<>();
        for (ReversionLogro reversionLogro : reversion.getLogros()) {
            usuarioLogroDAO.deleteByUsuarioYLogro(usuario.getUsuarioId(), reversionLogro.getLogroRef());
            logrosRetirados.add(reversionLogro.getLogroRef());
        }

        // Las monedas no se borran, se compensan: el libro es append-only.
        // Se permite saldo negativo.
        int monedasOtorgadas = reversion.getMonedasOtorgadas();
        if (monedasOtorgadas != 0) {
            usuarioMonedaService.registrarMovimiento(usuario, -monedasOtorgadas, "DESHACER_HABITO",
                    habito.getHabitoId(), "Deshecho: " + habito.getNombre());
        }

        if (reversion.getRachaActualPrevia() != null) {
            Racha racha = rachaDAO.findByHabito(habito);
            racha.setRachaActual(reversion.getRachaActualPrevia());
            racha.setRachaMaxima(reversion.getRachaMaximaPrevia());
            racha.setPeriodoMetaAlcanzada(reversion.getPeriodoMetaAlcanzadaPrevio());
            racha.setUltimaFecha(reversion.getUltimaFechaPrevia());
            rachaDAO.update(racha);
        }

        if (reversion.getMascotaExperienciaPrevia() != null) {
            mascotaService.restaurarProgreso(usuario.getUsuarioId(),
                    reversion.getMascotaExperienciaPrevia(), reversion.getMascotaDiaCompletoPrevio());
        }

        // La reversión primero y el registro después: importa por la FK.
        reversionRegistroDAO.delete(reversion.getReversionId());
        registroDAO.delete(registro.getRegistroId());

        return Map.of(
                "monedasDevueltas", monedasOtorgadas,
                "logrosRetirados", logrosRetirados
        );
    }
}
