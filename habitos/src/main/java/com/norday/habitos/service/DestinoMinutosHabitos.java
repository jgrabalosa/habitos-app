package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.gamificacion.model.DestinoDisponible;
import com.norday.gamificacion.model.ProgresoDestino;
import com.norday.gamificacion.model.ResultadoAporte;
import com.norday.gamificacion.service.DestinoMinutos;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.LimitesMinutos;
import com.norday.habitos.model.Objetivo;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Los hábitos de minutos como destino de los minutos de otra app (Norday
 * Focus): el adaptador de este dominio al puerto {@link DestinoMinutos} del
 * motor. Quien manda los minutos no conoce los hábitos, y este dominio no
 * conoce a quien los manda.
 *
 * {@link #aportar} valida antes de completar (dueño, que exista, que no esté
 * en pausa, que el día sea de minutos en la versión vigente ese día, que sea
 * de la semana en curso, no futuro y no anterior al alta del hábito, y de 1 a
 * 600 minutos) y responde
 * RECHAZADO con un código de motivo, que es una clave del bundle de habitos.
 * Si pasa, completa con {@link RegistroService#completar} igual que el panel
 * de minutos de la app (mismos minutos, misma clave, mismo premio), con origen
 * ENFOQUE.
 *
 * <h2>Transacción</h2>
 *
 * Todo {@link #aportar} corre dentro de una TransactionTemplate con
 * PROPAGATION_REQUIRES_NEW, y la excepción de negocio se captura <b>fuera</b>
 * de ella. Así:
 * <ul>
 *   <li>Si completar lanza (una pausa o un choque de clave que llega entre la
 *       validación y el completado), su transacción —solo la suya— se deshace
 *       entera, sin dejar nada a medias, y aquí se traduce a RECHAZADO.</li>
 *   <li>Es la lección de asegurarIdentidad del CLAUDE.md: la excepción de
 *       completar no puede dejar marcada para deshacer una transacción de
 *       quien llama.</li>
 *   <li>Capturar dentro no serviría: completar es @Transactional y, al lanzar,
 *       marca para deshacer la transacción en la que participa; si la captura
 *       estuviera dentro de la plantilla, su commit reventaría con
 *       UnexpectedRollbackException.</li>
 * </ul>
 * <b>Quien llama no puede tener una transacción abierta.</b> Completar
 * bloquea al usuario (y luego al hábito); con REQUIRES_NEW eso va por otra
 * conexión, y si la transacción de fuera ya tuviera al usuario bloqueado (el
 * alta de la sesión lo bloquea), las dos se esperarían hasta agotar el tope
 * de espera (lock_timeout) sin que Postgres lo vea como abrazo: son dos
 * conexiones del mismo hilo. Por eso {@link #aportar} lanza
 * IllegalStateException si al entrar hay una transacción activa. Sin
 * transacción exterior (que es como llama Norday Focus: registrar son tres
 * transacciones), REQUIRES_NEW no pide una segunda conexión.
 *
 * Un fallo técnico (base de datos caída, abrazo mortal) o un
 * IllegalArgumentException inesperado de completar no se traducen: salen como
 * excepción para que quien llama lo reintente, en vez de dejar apuntado para
 * siempre un rechazo que no lo era.
 */
@Service
public class DestinoMinutosHabitos implements DestinoMinutos {

    public static final String TIPO = "habito";

    // Los códigos de motivo: claves de mensajes/habitos, de 40 caracteres como
    // mucho (sesion_enfoque.aporte_motivo).
    public static final String MOTIVO_NO_ENCONTRADO = "aporte.habito.no.encontrado";
    public static final String MOTIVO_PAUSADO = "aporte.habito.pausado";
    public static final String MOTIVO_NO_MINUTOS = "aporte.habito.no.minutos";
    public static final String MOTIVO_DIA_FUTURO = "aporte.habito.dia.futuro";
    public static final String MOTIVO_SEMANA_PASADA = "aporte.habito.semana.pasada";
    public static final String MOTIVO_ANTES_ALTA = "aporte.habito.antes.alta";
    public static final String MOTIVO_MINUTOS_RANGO = "aporte.habito.minutos.rango";
    public static final String MOTIVO_ORIGEN = "aporte.habito.origen";
    public static final String MOTIVO_CONFLICTO = "aporte.habito.conflicto";

    public static final List<String> MOTIVOS = List.of(MOTIVO_NO_ENCONTRADO, MOTIVO_PAUSADO, MOTIVO_NO_MINUTOS,
            MOTIVO_DIA_FUTURO, MOTIVO_SEMANA_PASADA, MOTIVO_ANTES_ALTA, MOTIVO_MINUTOS_RANGO, MOTIVO_ORIGEN,
            MOTIVO_CONFLICTO);

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DestinoMinutosHabitos.class);

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private RegistroService registroService;

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private RachaService rachaService;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Override
    public String tipo() {
        return TIPO;
    }

    /**
     * Los hábitos de minutos activos (no en pausa) del usuario, con los
     * minutos que lleva hoy y los que pide hoy. "De minutos" según la versión
     * vigente hoy; hoy, en la zona del usuario. Solo lee: no pone al día la
     * copia del objetivo.
     */
    @Override
    public List<DestinoDisponible> disponibles(Long usuarioId) {
        TransactionTemplate lectura = new TransactionTemplate(transactionManager);
        lectura.setReadOnly(true);
        return lectura.execute(estado -> {
            Usuario usuario = usuarioId == null ? null : usuarioDAO.findById(usuarioId.intValue());
            if (usuario == null) return List.of();
            List<Habito> activos = habitoDAO.findActivos(usuario);
            if (activos.isEmpty()) return List.of();
            Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(activos);
            List<DestinoDisponible> disponibles = new ArrayList<>();
            for (Habito habito : activos) {
                LocalDate hoy = rachaService.hoyDe(habito);
                HistorialObjetivos historial = HistorialObjetivos.de(historiales, habito);
                Objetivo objetivo = historial.objetivoEl(hoy);
                if (!objetivo.enMinutos()) continue;
                Hechos hechos = Hechos.de(registroDAO.findByHabitoAndRango(habito, hoy, hoy), historial::objetivoEl);
                disponibles.add(new DestinoDisponible(TIPO, (long) habito.getHabitoId(), habito.getNombre(),
                        hechos.cantidadEl(hoy), objetivo.minutosDelDia()));
            }
            return disponibles;
        });
    }

    /**
     * Los minutos del día, su objetivo y si está hecho, con la versión vigente
     * ese día: lo mismo que responde completar a un reintento. Solo lee. Si
     * ese día el hábito no es de minutos, minutos y objetivo van a null.
     */
    @Override
    public ProgresoDestino progreso(Long usuarioId, Long destinoId, LocalDate dia) {
        TransactionTemplate lectura = new TransactionTemplate(transactionManager);
        lectura.setReadOnly(true);
        return lectura.execute(estado -> {
            Habito habito = delUsuario(usuarioId, destinoId);
            if (habito == null || dia == null) return null;
            HistorialObjetivos historial = HistorialObjetivos.de(
                    objetivoService.historiales(List.of(habito)), habito);
            Objetivo objetivo = historial.objetivoEl(dia);
            Hechos hechos = Hechos.de(registroDAO.findByHabitoAndRango(habito, dia, dia), historial::objetivoEl);
            return new ProgresoDestino(objetivo.enMinutos() ? hechos.cantidadEl(dia) : null,
                    objetivo.enMinutos() ? objetivo.minutosObjetivo() : null, hechos.diaHecho(dia));
        });
    }

    /** El hábito si existe y es del usuario; null si no (lo mismo en los dos casos). */
    private Habito delUsuario(Long usuarioId, Long destinoId) {
        Habito habito = destinoId == null || destinoId <= 0 || destinoId > Integer.MAX_VALUE
                ? null : habitoDAO.findById(destinoId.intValue());
        if (habito == null || usuarioId == null || habito.getPropietario() == null
                || habito.getPropietario().getUsuarioId() != usuarioId) {
            return null;
        }
        return habito;
    }

    /**
     * Ver la clase: corre en su propia transacción y quien llama no puede
     * tener una abierta (IllegalStateException si la tiene).
     */
    @Override
    public ResultadoAporte aportar(Long usuarioId, Long destinoId, int minutos, UUID clave, LocalDate dia,
                                   String origen) {
        if (org.springframework.transaction.support.TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("DestinoMinutosHabitos.aportar no se puede llamar con una transacción "
                    + "abierta: completar bloquea al usuario en otra conexión (REQUIRES_NEW) y se quedaría "
                    + "esperando a la de fuera hasta agotar el tope de espera. Llámalo fuera de la transacción.");
        }
        TransactionTemplate propia = new TransactionTemplate(transactionManager);
        propia.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        try {
            return propia.execute(estado -> aportarEnSuTransaccion(usuarioId, destinoId, minutos, clave, dia, origen));
        } catch (ConflictoException e) {
            log.info("Aporte de {} min al hábito {} rechazado al completar: {}", minutos, destinoId, e.getMessage());
            return ResultadoAporte.rechazado(MOTIVO_CONFLICTO);
        } catch (RecursoNoEncontradoException e) {
            return ResultadoAporte.rechazado(MOTIVO_NO_ENCONTRADO);
        }
        // Un IllegalArgumentException no se traduce: las reglas ya se han
        // mirado antes, así que es inesperado y sale como fallo técnico.
    }

    private ResultadoAporte aportarEnSuTransaccion(Long usuarioId, Long destinoId, int minutos, UUID clave,
                                                   LocalDate dia, String origenTexto) {
        // El dueño primero, y lo mismo si no existe que si es de otro: no se
        // dice qué ids hay.
        Habito habito = delUsuario(usuarioId, destinoId);
        if (habito == null) {
            return ResultadoAporte.rechazado(MOTIVO_NO_ENCONTRADO);
        }
        OrigenRegistro origen;
        try {
            origen = OrigenRegistro.valueOf(origenTexto);
        } catch (IllegalArgumentException | NullPointerException e) {
            return ResultadoAporte.rechazado(MOTIVO_ORIGEN);
        }
        if (!LimitesMinutos.valido(minutos)) {
            return ResultadoAporte.rechazado(MOTIVO_MINUTOS_RANGO);
        }

        // Como en completar: primero la clave. Si ya se usó, un reintento
        // responde lo mismo aunque desde entonces haya cambiado algo (una
        // pausa, el día): completar contesta REPETIDO sin crear ni pagar nada.
        boolean claveUsada = clave != null && registroDAO.findByClave(clave) != null;
        if (!claveUsada) {
            String motivo = motivoDeRechazo(habito, dia);
            if (motivo != null) {
                return ResultadoAporte.rechazado(motivo);
            }
        }

        Map<String, Object> resultado = registroService.completar(habito,
                new RegistroService.Solicitud(null, dia, String.valueOf(minutos), clave, origen));
        return traducir(resultado);
    }

    /**
     * Las reglas que completar haría cumplir lanzando, miradas antes para
     * responder con su propio motivo. null si se puede completar.
     */
    private String motivoDeRechazo(Habito habito, LocalDate dia) {
        if (!habito.isActivo()) {
            return MOTIVO_PAUSADO;
        }
        LocalDate hoy = rachaService.hoyDe(habito);
        if (dia == null || dia.isAfter(hoy)) {
            return MOTIVO_DIA_FUTURO;
        }
        // El mismo suelo que completar: el lunes de la semana en curso.
        LocalDate lunesDeEstaSemana = hoy.minusDays(hoy.getDayOfWeek().getValue() - 1L);
        if (dia.isBefore(lunesDeEstaSemana)) {
            return MOTIVO_SEMANA_PASADA;
        }
        // Ni un día anterior al alta del hábito, como en completar: un
        // hábito creado el viernes no existía de lunes a jueves.
        if (habito.getFechaInicio() != null && dia.isBefore(habito.getFechaInicio())) {
            return MOTIVO_ANTES_ALTA;
        }
        // De minutos según la versión vigente ESE día, no la de hoy: en veces,
        // completar no rechazaría, contaría una vez e ignoraría los minutos.
        HistorialObjetivos historial = HistorialObjetivos.de(objetivoService.historiales(List.of(habito)), habito);
        if (!historial.objetivoEl(dia).enMinutos()) {
            return MOTIVO_NO_MINUTOS;
        }
        return null;
    }

    /** La respuesta de completar, en el idioma del puerto. */
    @SuppressWarnings("unchecked")
    private static ResultadoAporte traducir(Map<String, Object> resultado) {
        boolean repetido = Boolean.TRUE.equals(resultado.get("repetido"));
        return new ResultadoAporte(
                repetido ? ResultadoAporte.Estado.REPETIDO : ResultadoAporte.Estado.ACEPTADO,
                null,
                entero(resultado.get("puntosGanados")),
                entero(resultado.get("xpGanada")),
                (Integer) resultado.get("minutosDia"),
                (Integer) resultado.get("minutosObjetivo"),
                (Boolean) resultado.get("diaHecho"),
                (List<String>) resultado.get("logros"));
    }

    private static int entero(Object valor) {
        return valor instanceof Number numero ? numero.intValue() : 0;
    }
}
