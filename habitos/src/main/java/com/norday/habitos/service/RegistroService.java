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
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
    private static final int XP_POR_DIA_COMPLETADO = 5;

    @Transactional
    public Map<String, Object> completarHabito(Habito habito, String nota) {
        ZoneId zona = rachaService.zonaDe(habito);
        LocalDate hoy = LocalDate.now(zona);
        Usuario usuario = habito.getPropietario();

        // SEMANAL: máximo un completado por día (cada completado es un día distinto)
        if (habito.getFrecuencia() == Frecuencia.SEMANAL
                && registroDAO.existeRegistroEnFecha(habito, hoy)) {
            throw new ConflictoException("Este hábito ya se ha completado hoy");
        }

        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(zona);
        int completadosAntes = registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
        int meta = habito.getMeta();

        // DIARIO: no dejar superar la meta del día. Sin esto, un doble toque o
        // un reintento por timeout de red crea un registro de más y, si
        // coincide justo con el punto en que se alcanza la meta, otorga
        // puntos por partida doble.
        if (habito.getFrecuencia() == Frecuencia.DIARIO && completadosAntes >= meta) {
            throw new ConflictoException("Este hábito ya se ha completado hoy");
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
        Mascota mascotaPrevia = mascotaService.obtenerOCrear(usuario.getUsuarioId());
        int mascotaExperienciaPrevia = mascotaPrevia.getExperiencia();
        LocalDate mascotaDiaCompletoPrevio = mascotaPrevia.getFechaUltimoDiaCompleto();

        Registro registro = new Registro(habito, true, nota, hoy);
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

        boolean metaAlcanzadaAhora = actualizarRacha(habito, completadosAntes + 1, meta, zona, hoy);
        if (metaAlcanzadaAhora) {
            puntosGanados += otorgarPuntosPorHitoRacha(usuario, habito);
        }

        // Con este registro puede haberse cerrado el día entero. Va después de
        // guardar el Registro a propósito: la consulta de esDiaCompleto es JPQL,
        // así que Hibernate hace flush antes y el registro recién creado cuenta.
        if (habitoService.esDiaCompleto(usuario)) {
            mascotaService.registrarDiaCompleto(usuario.getUsuarioId());
        }

        // Los logros de racha leen rachaActual/rachaMaxima en crudo, pero aquí
        // ya es seguro: actualizarRacha acaba de normalizarla en esta misma
        // llamada, así que no puede haber valores rancios.
        List<String> logros = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

        // Cuándo mostrar el sheet de valoración: SEMANAL siempre (cada completado es
        // un día distinto), DIARIO solo en el último completado del día (al llegar a la meta)
        boolean mostrarValoracion = habito.getFrecuencia() == Frecuencia.SEMANAL
                || (completadosAntes + 1) >= meta;

        // Cierra la instantánea con lo que efectivamente ocurrió: el delta de
        // saldo recoge de una vez el completado, el hito de racha y los logros.
        int monedasOtorgadas = usuarioMonedaService.consultarSaldo(usuario.getUsuarioId()) - saldoAntes;
        ReversionRegistro reversion = new ReversionRegistro(registro, rachaActualPrevia, rachaMaximaPrevia,
                periodoMetaAlcanzadaPrevio, mascotaExperienciaPrevia, mascotaDiaCompletoPrevio, monedasOtorgadas);
        for (String codigo : logros) {
            Logro logro = logroDAO.findByCodigo(codigo);
            if (logro != null) {
                reversion.getLogros().add(new ReversionLogro(reversion, logro.getLogroId()));
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

    public int contarCompletadosPeriodoActual(Habito habito) {
        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(rachaService.zonaDe(habito));
        return registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
    }

    /**
     * Actualiza la racha SOLO si se alcanza la meta del periodo por primera vez en ese periodo.
     * Devuelve true si la racha acaba de subir en esta llamada (para disparar puntos de hito).
     */
    private boolean actualizarRacha(Habito habito, int completadosEnPeriodo, int meta,
                                    ZoneId zona, LocalDate hoy) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return false;

        if (racha.metaAlcanzadaEnPeriodoActual(zona)) {
            return false; // ya subió este periodo, completar de más no hace nada
        }

        if (completadosEnPeriodo >= meta) {
            // Rotura perezosa: si se saltó un periodo entero, la racha no
            // continúa desde el valor viejo — vuelve a empezar en 1.
            int base = racha.sigueViva(zona) ? racha.getRachaActual() : 0;
            racha.setRachaActual(base + 1);
            if (racha.getRachaActual() > racha.getRachaMaxima()) {
                racha.setRachaMaxima(racha.getRachaActual());
            }
            racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().rangoPeriodoActual(zona)[0]);
            racha.setUltimaFecha(hoy);
            rachaDAO.update(racha);
            return true;
        }

        return false;
    }

    private int otorgarPuntosPorHitoRacha(Usuario usuario, Habito habito) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return 0;

        int actual = racha.getRachaActual();
        int puntos = switch (actual) {
            case 3 -> 50;
            case 7 -> 100;
            case 30 -> 300;
            case 100 -> 750;
            case 365 -> 2000;
            default -> 0;
        };

        if (puntos > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, puntos, "HITO_RACHA", habito.getHabitoId(),
                    "Hito de racha (" + actual + ") en: " + habito.getNombre()
            );
        }
        return puntos;
    }

    public boolean estaCompletadoHoy(Habito habito) {
        return registroDAO.existeRegistroEnFecha(habito, LocalDate.now(rachaService.zonaDe(habito)));
    }

    public List<Registro> obtenerRegistros(Habito habito) {
        return registroDAO.findByHabito(habito);
    }

    public Registro obtenerPorFecha(Habito habito, LocalDate fecha) {
        return registroDAO.findByHabitoAndFecha(habito, fecha);
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
     * instantánea capturada al completarlo. Solo el último registro del
     * hábito, y solo si es de hoy: fuera de eso la instantánea no es fiel y
     * el endpoint corrompería datos en silencio.
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

        // Y solo si es de hoy, en la zona horaria del hábito.
        ZoneId zona = rachaService.zonaDe(habito);
        if (!registro.getFecha().equals(LocalDate.now(zona))) {
            throw new ConflictoException("Solo se puede deshacer un completado de hoy");
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