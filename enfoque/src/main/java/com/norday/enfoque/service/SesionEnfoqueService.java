package com.norday.enfoque.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.TextosService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.enfoque.model.EstadoAporte;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.MotivosEnfoque;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.model.dto.PeticionSesionDTO;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.enfoque.service.PremioEnfoqueService.Premio;
import com.norday.gamificacion.model.DestinoDisponible;
import com.norday.gamificacion.model.ProgresoDestino;
import com.norday.gamificacion.model.ResultadoAporte;
import com.norday.gamificacion.service.DestinoMinutos;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * Las sesiones de concentración de Norday Focus: guardarlas, pagar su premio,
 * llevar sus minutos a su destino y contarlas.
 *
 * <h2>Registrar una sesión: tres pasos, ninguna transacción grande</h2>
 *
 * {@link #registrar} no es {@code @Transactional} a propósito:
 * <ol>
 *   <li>Guarda la sesión en una transacción corta, idempotente por clave
 *       (INSERT … ON CONFLICT), y en la misma transacción paga el premio de
 *       la sesión (PremioEnfoqueService: monedas, XP y logros de Focus, con
 *       el usuario ya bloqueado). Si hay destino y la sesión cuenta, queda
 *       con el aporte PENDIENTE. Al confirmar, la sesión y su premio ya
 *       existen pase lo que pase después.</li>
 *   <li>Lleva los minutos al destino con {@link DestinoMinutos#aportar}, que
 *       corre en su propia transacción.</li>
 *   <li>Apunta el resultado en la sesión, en otra transacción corta.</li>
 * </ol>
 *
 * Por qué así y no todo junto: si el destino rechaza algo lanzando una
 * excepción dentro de una transacción compartida, esa transacción queda
 * marcada para deshacer y el commit de la sesión revienta con
 * UnexpectedRollbackException («if an inner transaction (of which the outer
 * caller is not aware) silently marks a transaction as rollback-only, the
 * outer caller still calls commit. The outer caller needs to receive an
 * UnexpectedRollbackException», documentación de Spring sobre propagación,
 * https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/tx-propagation.html).
 * Es la lección de asegurarIdentidad en el CLAUDE.md. Sin transacción
 * exterior, la sesión ya está confirmada cuando se aporta y ningún rechazo
 * puede llevársela por delante.
 *
 * Tampoco hace falta REQUIRES_NEW aquí: la misma página avisa de que la
 * transacción interior pide su propia conexión mientras la exterior retiene la
 * suya, y que con el pool justo eso puede acabar en un abrazo mortal. Sin
 * exterior, cada petición usa una sola conexión.
 *
 * Si el proceso muere entre el paso 2 y el 3, la sesión queda PENDIENTE y los
 * minutos ya están en el destino. El reintento con la misma clave lo completa:
 * el destino responde REPETIDO por la clave y no suma ni paga otra vez.
 *
 * Orden de bloqueo: sesión → destino. El paso 1 bloquea el alta de sesiones
 * del usuario y lo suelta al confirmar; el destino bloquea lo suyo en el paso
 * 2. Ninguna transacción tiene los dos a la vez, así que no pueden abrazarse.
 */
@Service
public class SesionEnfoqueService {

    /** El origen con el que llegan los minutos al destino. */
    public static final String ORIGEN = "ENFOQUE";

    public static final int OBJETIVO_MINIMO = 10;
    public static final int OBJETIVO_MAXIMO = 120;
    public static final int OBJETIVO_PASO = 5;
    /** Lo mínimo para que una sesión cuente: diez minutos. */
    public static final int DURACION_MINIMA_SEG = 10 * 60;
    /** La duración máxima (120 min) más un minuto de margen para el reloj. */
    public static final int DURACION_MAXIMA_SEG = OBJETIVO_MAXIMO * 60 + 60;
    /** Cuánto puede adelantarse el reloj del móvil al del servidor. */
    public static final Duration MARGEN_FUTURO = Duration.ofMinutes(2);
    /** Lo más antigua que puede llegar una sesión (la cola sin conexión). */
    public static final Duration ANTIGUEDAD_MAXIMA = Duration.ofDays(7);
    public static final int DIAS_MAXIMOS_RESUMEN = 62;
    public static final int TAMANO_MAXIMO_PAGINA = 100;
    /** La última página del historial que se sirve. */
    public static final int PAGINA_MAXIMA = 10000;
    /** Lo que cabe en destino_tipo. */
    public static final int LONGITUD_MAXIMA_TIPO = 20;

    /** UUID en su forma canónica: UUID.fromString admite formas raras como "1-1-1-1-1". */
    private static final Pattern UUID_CANONICO =
            Pattern.compile("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$");

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(SesionEnfoqueService.class);

    @Autowired
    private ISesionEnfoqueDAO sesionDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    /** Spring recolecta aquí los destinos de todos los dominios. */
    @Autowired
    private List<DestinoMinutos> destinos;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Autowired
    private TextosService textosService;

    @Autowired
    private RelojEnfoque reloj;

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private PremioEnfoqueService premioService;

    /** Una petición ya leída y con sus formatos validados. */
    public record Peticion(UUID clave, Instant inicio, int duracionSeg, int duracionObjetivoMin, EstadoSesion estado,
                           String destinoTipo, Long destinoId) {}

    /**
     * Lo que pasó con los minutos de una sesión, para la respuesta.
     * {@code minutos} son los que están en el destino por esta sesión, y
     * monedas, XP y logros, lo que pagó el destino al recibirlos: en un
     * reintento, los guardados (el resultado original). El progreso del día
     * ({@code minutosDia}, {@code minutosObjetivo}, {@code diaHecho}) se lee
     * en el momento: en un reintento, cómo va ahora el día del destino.
     */
    public record Aporte(EstadoAporte estado, String motivo, int minutos, int monedas, int xp, Integer minutosDia,
                         Integer minutosObjetivo, Boolean diaHecho, List<String> logros) {}

    /**
     * El resultado de registrar: la sesión, su aporte (lo del destino), su
     * premio (lo de la sesión) y si se ha creado ahora (201) o ya existía
     * (200). En un reintento, el premio es el guardado.
     */
    public record Resultado(SesionEnfoque sesion, Aporte aporte, Premio premio, boolean nueva) {}

    public record DiaResumen(LocalDate dia, int minutos, int sesiones) {}

    public record Resumen(List<DiaResumen> dias, int totalMinutos, int totalSesiones, int rachaActual) {}

    public record Pagina(List<SesionEnfoque> sesiones, int pagina, int tamano, long total, boolean hayMas) {}

    private record Alta(SesionEnfoque sesion, boolean nueva, Premio premio) {}

    // ── Destinos ─────────────────────────────────────────────────

    /** Los destinos a los que el usuario puede llevar minutos hoy, de todos los dominios. */
    public List<DestinoDisponible> destinos(Usuario usuario) {
        List<DestinoDisponible> todos = new ArrayList<>();
        for (DestinoMinutos destino : destinos) {
            todos.addAll(destino.disponibles((long) usuario.getUsuarioId()));
        }
        return todos;
    }

    // ── Registrar ────────────────────────────────────────────────

    public Resultado registrar(Usuario usuario, PeticionSesionDTO dto) {
        Peticion peticion = leer(dto, usuario);

        // Paso 1: la sesión, confirmada antes de tocar el destino.
        Alta alta = new TransactionTemplate(transactionManager).execute(estado -> guardar(usuario, peticion));
        SesionEnfoque sesion = alta.sesion();
        if (sesion.getAporteEstado() != EstadoAporte.PENDIENTE) {
            return new Resultado(sesion, aporteGuardado(sesion), alta.premio(), alta.nueva());
        }

        // Paso 2: el destino, en su propia transacción. Una excepción aquí es
        // técnica (el destino no lanza por sus reglas): sale como
        // DestinoNoDisponibleException (503) y la sesión queda PENDIENTE para
        // el reintento con la misma clave.
        ResultadoAporte resultado;
        try {
            resultado = aportar(sesion);
        } catch (RuntimeException e) {
            log.error("No se han podido llevar los minutos de la sesión {} a su destino", sesion.getId(), e);
            throw new DestinoNoDisponibleException(e);
        }

        // Paso 3: el resultado, en la sesión.
        EstadoAporte estado = EstadoAporte.valueOf(resultado.estado().name());
        int minutos = estado.minutosEnDestino() ? sesion.minutos() : 0;
        sesionDAO.guardarAporte(sesion.getId(), estado, resultado.motivo(), minutos, resultado.monedas(),
                resultado.xp(), SesionEnfoque.textoDeLogros(resultado.logros()));
        SesionEnfoque guardada = sesionDAO.releer(sesion.getId());
        // Si otro reintento simultáneo apuntó antes otro resultado, manda el
        // guardado: la respuesta no puede contradecir a la base de datos.
        Aporte aporte = guardada.getAporteEstado() == estado
                ? new Aporte(estado, resultado.motivo(), guardada.getMinutosAportados(), resultado.monedas(),
                        resultado.xp(), resultado.minutosDia(), resultado.minutosObjetivo(), resultado.diaHecho(),
                        resultado.logros())
                : aporteGuardado(guardada);
        return new Resultado(guardada, aporte, alta.premio(), alta.nueva());
    }

    /**
     * Paso 1, dentro de su transacción. Con la clave ya usada devuelve la
     * sesión guardada si el cuerpo es el mismo, y 409 si no. Las reglas de
     * tiempo y el solape solo se miran al crearla: un reintento puede llegar
     * días después.
     */
    private Alta guardar(Usuario usuario, Peticion peticion) {
        // Las altas de sesiones del usuario, de una en una, para que dos no
        // pasen a la vez la comprobación de solape: el bloqueo del usuario del
        // motor (FOR NO KEY UPDATE, con su tope de espera), el mismo que
        // completar y todo lo que le da monedas o logros. Si la cuenta se ha
        // borrado mientras se esperaba, 404: no es un fallo para reintentar.
        if (!usuarioDAO.bloquear(usuario.getUsuarioId())) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }
        SesionEnfoque existente = sesionDAO.findByClave(peticion.clave());
        if (existente != null) {
            return yaGuardada(laMisma(existente, usuario, peticion));
        }

        validarTiempo(peticion, usuario);
        LocalDateTime inicio = LocalDateTime.ofInstant(peticion.inicio(), ZoneOffset.UTC);
        LocalDateTime fin = inicio.plusSeconds(peticion.duracionSeg());
        if (sesionDAO.haySolape(usuario.getUsuarioId(), inicio, fin)) {
            throw new ConflictoException(textosService.texto("enfoque.sesion.solape", usuario));
        }

        SesionEnfoque sesion = new SesionEnfoque();
        sesion.setUsuario(usuario);
        sesion.setClave(peticion.clave());
        sesion.setInicioUtc(inicio);
        sesion.setDuracionSeg(peticion.duracionSeg());
        sesion.setDuracionObjetivoMin(peticion.duracionObjetivoMin());
        sesion.setEstado(peticion.estado());
        sesion.setDia(diaDe(peticion.inicio(), usuario));
        sesion.setDestinoTipo(peticion.destinoTipo());
        sesion.setDestinoRef(peticion.destinoId());
        sesion.setCreadoEn(LocalDateTime.ofInstant(reloj.ahora(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS));
        if (peticion.destinoTipo() == null) {
            sesion.setAporteEstado(EstadoAporte.SIN_DESTINO);
        } else if (!peticion.estado().cuenta()) {
            sesion.setAporteEstado(EstadoAporte.RECHAZADO);
            sesion.setAporteMotivo(MotivosEnfoque.SESION_PERDIDA);
        } else {
            sesion.setAporteEstado(EstadoAporte.PENDIENTE);
        }
        sesion.setMinutosAportados(0);
        sesion.setMonedasAportadas(0);
        sesion.setXpAportada(0);
        sesion.setLogrosAportados(null);

        Integer id = sesionDAO.insertarSiClaveLibre(sesion);
        if (id == null) {
            // La clave la ha guardado otra cuenta entretanto (el bloqueo es
            // por usuario): no es esta sesión.
            return yaGuardada(laMisma(sesionDAO.findByClave(peticion.clave()), usuario, peticion));
        }
        SesionEnfoque nueva = sesionDAO.findById(id);
        // El premio de la sesión, aquí: dentro de la transacción del alta y con
        // el usuario bloqueado desde el principio. Si falla, no queda nada y
        // el reintento lo hace entero.
        Premio premio = premioService.pagar(usuario, nueva);
        return new Alta(nueva, true, premio);
    }

    /** Una sesión que ya estaba guardada: su premio es el guardado, sin pagar otra vez. */
    private Alta yaGuardada(SesionEnfoque guardada) {
        return new Alta(guardada, false, PremioEnfoqueService.guardado(guardada));
    }

    /** La sesión guardada con esa clave, si es la misma petición; si no, 409. */
    private SesionEnfoque laMisma(SesionEnfoque guardada, Usuario usuario, Peticion peticion) {
        boolean misma = guardada != null
                && guardada.getUsuario().getUsuarioId() == usuario.getUsuarioId()
                && guardada.getInicioUtc().equals(LocalDateTime.ofInstant(peticion.inicio(), ZoneOffset.UTC))
                && guardada.getDuracionSeg() == peticion.duracionSeg()
                && guardada.getDuracionObjetivoMin() == peticion.duracionObjetivoMin()
                && guardada.getEstado() == peticion.estado()
                && Objects.equals(guardada.getDestinoTipo(), peticion.destinoTipo())
                && Objects.equals(guardada.getDestinoRef(), peticion.destinoId());
        if (!misma) {
            throw new ConflictoException(textosService.texto("enfoque.sesion.clave.otra", usuario));
        }
        return guardada;
    }

    /** El destino que declara ese tipo; null si ninguno. */
    private DestinoMinutos destinoDe(String tipo) {
        return destinos.stream().filter(d -> d.tipo().equals(tipo)).findFirst().orElse(null);
    }

    /** Paso 2: el destino del tipo de la sesión; un tipo que nadie declara, RECHAZADO. */
    private ResultadoAporte aportar(SesionEnfoque sesion) {
        DestinoMinutos destino = destinoDe(sesion.getDestinoTipo());
        if (destino == null) {
            return ResultadoAporte.rechazado(MotivosEnfoque.DESTINO_DESCONOCIDO);
        }
        return destino.aportar((long) sesion.getUsuario().getUsuarioId(), sesion.getDestinoRef(), sesion.minutos(),
                sesion.getClave(), sesion.getDia(), ORIGEN);
    }

    /**
     * El aporte tal como está guardado (lo que pagó el destino la primera
     * vez), con el progreso del día leído ahora del destino si los minutos
     * están allí. Solo lee: no vuelve a aportar, porque el registro podría
     * haberse deshecho y se sumaría otra vez.
     */
    private Aporte aporteGuardado(SesionEnfoque sesion) {
        ProgresoDestino progreso = null;
        if (sesion.getAporteEstado().minutosEnDestino()) {
            DestinoMinutos destino = destinoDe(sesion.getDestinoTipo());
            if (destino != null) {
                progreso = destino.progreso((long) sesion.getUsuario().getUsuarioId(), sesion.getDestinoRef(),
                        sesion.getDia());
            }
        }
        return new Aporte(sesion.getAporteEstado(), sesion.getAporteMotivo(), sesion.getMinutosAportados(),
                sesion.getMonedasAportadas(), sesion.getXpAportada(),
                progreso != null ? progreso.minutosDia() : null,
                progreso != null ? progreso.minutosObjetivo() : null,
                progreso != null ? progreso.diaHecho() : null,
                sesion.logros());
    }

    /** El día de inicio en la zona del usuario: el día al que van los minutos. */
    private LocalDate diaDe(Instant inicio, Usuario usuario) {
        return inicio.atZone(zonaUsuarioService.zonaDe(usuario)).toLocalDate();
    }

    // ── Validación ───────────────────────────────────────────────

    /** Formatos y rangos que no dependen del reloj. Cualquier fallo, 400 con texto traducido. */
    Peticion leer(PeticionSesionDTO dto, Usuario usuario) {
        if (dto == null || dto.clave() == null || !UUID_CANONICO.matcher(dto.clave().trim()).matches()) {
            throw invalida("enfoque.peticion.clave", usuario);
        }
        UUID clave = UUID.fromString(dto.clave().trim());

        Instant inicio;
        try {
            inicio = dto.inicioUtc() == null ? null : Instant.parse(dto.inicioUtc().trim());
        } catch (DateTimeParseException e) {
            inicio = null;
        }
        if (inicio == null) {
            throw invalida("enfoque.peticion.inicio", usuario);
        }
        // Postgres guarda microsegundos: así el reintento compara igual.
        inicio = inicio.truncatedTo(ChronoUnit.MICROS);

        EstadoSesion estado = null;
        if (dto.estado() != null) {
            try {
                estado = EstadoSesion.valueOf(dto.estado().trim());
            } catch (IllegalArgumentException e) {
                estado = null;
            }
        }
        if (estado == null) {
            throw invalida("enfoque.peticion.estado", usuario);
        }

        Integer objetivo = dto.duracionObjetivoMin();
        if (objetivo == null || objetivo < OBJETIVO_MINIMO || objetivo > OBJETIVO_MAXIMO
                || objetivo % OBJETIVO_PASO != 0) {
            throw invalida("enfoque.peticion.objetivo", usuario, OBJETIVO_MINIMO, OBJETIVO_MAXIMO, OBJETIVO_PASO);
        }

        Integer duracion = dto.duracionSeg();
        if (duracion == null || duracion < 0) {
            throw invalida("enfoque.peticion.duracion", usuario);
        }
        if (duracion > DURACION_MAXIMA_SEG) {
            throw invalida("enfoque.peticion.duracion.maxima", usuario, OBJETIVO_MAXIMO);
        }
        if (estado.cuenta() && duracion < DURACION_MINIMA_SEG) {
            throw invalida("enfoque.peticion.duracion.minima", usuario, DURACION_MINIMA_SEG / 60);
        }
        // Sin pausas, una sesión no puede durar más que lo elegido; el minuto
        // de margen es para el reloj, como en la duración máxima.
        if (duracion > objetivo * 60 + 60) {
            throw invalida("enfoque.peticion.duracion.objetivo", usuario, objetivo);
        }

        String destinoTipo = null;
        Long destinoId = null;
        if (dto.destino() != null) {
            String tipo = dto.destino().tipo() == null ? "" : dto.destino().tipo().trim();
            Long id = dto.destino().id();
            if (tipo.isEmpty() || tipo.length() > LONGITUD_MAXIMA_TIPO || id == null || id <= 0) {
                throw invalida("enfoque.peticion.destino", usuario);
            }
            destinoTipo = tipo;
            destinoId = id;
        }
        return new Peticion(clave, inicio, duracion, objetivo, estado, destinoTipo, destinoId);
    }

    /** Las reglas de tiempo de una sesión nueva, contra el reloj del servidor. */
    private void validarTiempo(Peticion peticion, Usuario usuario) {
        Instant ahora = reloj.ahora();
        Instant fin = peticion.inicio().plusSeconds(peticion.duracionSeg());
        if (fin.isAfter(ahora.plus(MARGEN_FUTURO))) {
            throw invalida("enfoque.peticion.futura", usuario);
        }
        if (peticion.inicio().isBefore(ahora.minus(ANTIGUEDAD_MAXIMA))) {
            throw invalida("enfoque.peticion.antigua", usuario, ANTIGUEDAD_MAXIMA.toDays());
        }
    }

    private SesionInvalidaException invalida(String clave, Usuario usuario, Object... args) {
        return new SesionInvalidaException(textosService.texto(clave, usuario, args));
    }

    // ── Resumen e historial ──────────────────────────────────────

    /**
     * Minutos y sesiones por día entre dos fechas (como mucho 62 días), sus
     * totales y la racha de enfoque. Todo cuenta solo COMPLETADA y TERMINADA.
     * Salen todos los días del rango, también los que no tienen sesiones.
     */
    public Resumen resumen(Usuario usuario, String desdeTexto, String hastaTexto) {
        LocalDate desde;
        LocalDate hasta;
        try {
            desde = LocalDate.parse(desdeTexto);
            hasta = LocalDate.parse(hastaTexto);
        } catch (DateTimeParseException | NullPointerException e) {
            throw invalida("enfoque.resumen.fechas", usuario);
        }
        if (hasta.isBefore(desde) || ChronoUnit.DAYS.between(desde, hasta) + 1 > DIAS_MAXIMOS_RESUMEN) {
            throw invalida("enfoque.resumen.rango", usuario, DIAS_MAXIMOS_RESUMEN);
        }

        Map<LocalDate, int[]> porDia = new LinkedHashMap<>();
        for (LocalDate dia = desde; !dia.isAfter(hasta); dia = dia.plusDays(1)) {
            porDia.put(dia, new int[2]);
        }
        int totalMinutos = 0;
        int totalSesiones = 0;
        for (SesionEnfoque sesion : sesionDAO.findByUsuarioEntre(usuario.getUsuarioId(), desde, hasta)) {
            if (!sesion.getEstado().cuenta()) continue;
            int[] cuenta = porDia.get(sesion.getDia());
            if (cuenta == null) continue;
            cuenta[0] += sesion.minutos();
            cuenta[1]++;
            totalMinutos += sesion.minutos();
            totalSesiones++;
        }
        List<DiaResumen> dias = new ArrayList<>();
        porDia.forEach((dia, cuenta) -> dias.add(new DiaResumen(dia, cuenta[0], cuenta[1])));
        return new Resumen(dias, totalMinutos, totalSesiones, rachaActual(usuario));
    }

    /**
     * Días seguidos con al menos una sesión que cuenta, terminando hoy o ayer
     * (en la zona del usuario): hoy todavía no ha acabado, así que no tener
     * sesión hoy no rompe la racha de ayer.
     */
    int rachaActual(Usuario usuario) {
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        LocalDate hoy = LocalDate.ofInstant(reloj.ahora(), zona);
        List<LocalDate> dias = sesionDAO.diasQueCuentanHasta(usuario.getUsuarioId(), hoy);
        if (dias.isEmpty()) return 0;
        LocalDate esperado = dias.get(0);
        if (!esperado.equals(hoy) && !esperado.equals(hoy.minusDays(1))) return 0;
        int racha = 0;
        for (LocalDate dia : dias) {
            if (!dia.equals(esperado)) break;
            racha++;
            esperado = esperado.minusDays(1);
        }
        return racha;
    }

    /** Una página del historial, de la más reciente a la más antigua. */
    public Pagina historial(Usuario usuario, int pagina, int tamano) {
        if (pagina < 0 || pagina > PAGINA_MAXIMA || tamano < 1 || tamano > TAMANO_MAXIMO_PAGINA) {
            throw invalida("enfoque.historial.pagina", usuario, PAGINA_MAXIMA, TAMANO_MAXIMO_PAGINA);
        }
        List<SesionEnfoque> sesiones = sesionDAO.pagina(usuario.getUsuarioId(), pagina, tamano);
        long total = sesionDAO.contar(usuario.getUsuarioId());
        return new Pagina(sesiones, pagina, tamano, total, ((long) pagina + 1) * tamano < total);
    }
}
