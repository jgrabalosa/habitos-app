package com.norday.habitos.service;

import com.norday.core.model.AppNorday;
import com.norday.core.model.Usuario;
import com.norday.core.service.DispositivoService;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.TextosService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.AvisoApp;
import com.norday.habitos.model.CalculoAvisos;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Hechos;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.TipoAviso;
import com.norday.habitos.repository.IAvisoAppDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Los avisos que la app manda por iniciativa propia, distintos de los
 * recordatorios que el usuario pone a cada hábito ({@link NotificacionScheduler}):
 * la vuelta y el momento débil. Nunca uno por hábito. Las reglas están en
 * {@link CalculoAvisos} y en docs/Logicas/03, «Avisos de la app».
 *
 * Cada usuario se evalúa con su hora local, no con la del servidor: el barrido
 * corre cada hora en punto y a cada uno solo le toca cuando en su reloj son las
 * 19:00 (la vuelta) o los viernes las 18:00 (el momento débil). Solo a quien
 * tiene los avisos encendidos, token de Norday Habits y algún hábito activo;
 * nunca al token de otra app.
 */
@Component
public class AvisosAppScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(AvisosAppScheduler.class);

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IAvisoAppDAO avisoAppDAO;

    @Autowired
    private DispositivoService dispositivoService;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Autowired
    private TextosService textosService;

    @Autowired
    private ObjetivoService objetivoService;

    /** El reloj del barrido programado; los tests lo sustituyen (o llaman a {@link #enviarAvisos(Instant)}). */
    private Clock reloj = Clock.systemUTC();

    public void setReloj(Clock reloj) {
        this.reloj = reloj;
    }

    /**
     * Barrido cada hora en punto. El cron no lleva zona porque cada hora es
     * cada hora en cualquier zona; quien decide es la hora local de cada
     * usuario, dentro.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void enviarAvisos() {
        enviarAvisos(Instant.now(reloj));
    }

    /**
     * El barrido tal como lo vería el reloj en ese instante. Separado del
     * programado para que los tests fijen la hora.
     */
    public void enviarAvisos(Instant ahora) {
        // Quién está en su hora, con los avisos encendidos y algún hábito activo.
        Map<Integer, Candidato> candidatos = candidatosEnSuHora(ahora);
        if (candidatos.isEmpty()) {
            return;
        }

        // El token de Norday Habits, no el de otra app del ecosistema. Una
        // consulta para todos.
        Map<Integer, String> tokens = dispositivoService.tokensDe(candidatos.keySet(), AppNorday.HABITOS);
        candidatos.keySet().removeIf(id -> {
            String token = tokens.get(id);
            return token == null || token.isBlank();
        });
        if (candidatos.isEmpty()) {
            return;
        }

        Map<Integer, List<AvisoApp>> avisosPorUsuario = avisoAppDAO.findByUsuarios(candidatos.keySet());
        Map<Integer, LocalDate> ultimaActividad = candidatos.values().stream().anyMatch(c -> c.esVuelta)
                ? registroDAO.ultimaFechaCompletadaPorUsuario(candidatos.keySet())
                : Map.of();

        // Los límites valen para todos los avisos de la app: se aplican antes de
        // calcular nada más.
        List<Candidato> sinLimite = new ArrayList<>();
        for (Candidato candidato : candidatos.values()) {
            List<AvisoApp> avisos = avisosPorUsuario.getOrDefault(candidato.usuarioId(), List.of());
            if (CalculoAvisos.topeAlcanzado(avisos, candidato.hoy, candidato.zona)
                    || estaRetirada(candidato, avisos, ahora)) {
                continue;
            }
            sinLimite.add(candidato);
        }

        Map<Integer, List<Habito>> aTiroPorUsuario = semanalesATiro(sinLimite);

        int enviados = 0;
        for (Candidato candidato : sinLimite) {
            try {
                List<AvisoApp> avisos = avisosPorUsuario.getOrDefault(candidato.usuarioId(), List.of());
                if (candidato.esVuelta) {
                    TipoAviso tipo = CalculoAvisos.vuelta(candidato.hoy, ultimaActividad.get(candidato.usuarioId()),
                            avisos, candidato.zona);
                    if (tipo != null && enviar(candidato, tipo, avisos, tokens.get(candidato.usuarioId()), List.of())) {
                        enviados++;
                    }
                } else {
                    List<Habito> aTiro = aTiroPorUsuario.getOrDefault(candidato.usuarioId(), List.of());
                    if (!aTiro.isEmpty() && enviar(candidato, TipoAviso.MOMENTO_DEBIL, avisos,
                            tokens.get(candidato.usuarioId()), aTiro)) {
                        enviados++;
                    }
                }
            } catch (RuntimeException e) {
                // Un usuario con un dato malo no puede dejar sin aviso a los demás.
                log.warn("No se pudo mandar el aviso de la app al usuario {}", candidato.usuarioId(), e);
            }
        }

        if (enviados > 0) {
            log.info("Avisos de la app enviados: {}", enviados);
        }
    }

    /**
     * Los usuarios con avisos encendidos y algún hábito activo a quienes, en su
     * reloj, les toca la vuelta o el momento débil.
     */
    private Map<Integer, Candidato> candidatosEnSuHora(Instant ahora) {
        Map<Integer, List<Habito>> habitosPorUsuario = new LinkedHashMap<>();
        Map<Integer, Usuario> usuarios = new HashMap<>();
        for (Habito habito : habitoDAO.findTodosActivos()) {
            Usuario propietario = habito.getPropietario();
            if (propietario == null || !propietario.isAvisosApp()) {
                continue;
            }
            usuarios.put(propietario.getUsuarioId(), propietario);
            habitosPorUsuario.computeIfAbsent(propietario.getUsuarioId(), id -> new ArrayList<>()).add(habito);
        }

        Map<Integer, Candidato> candidatos = new LinkedHashMap<>();
        for (Map.Entry<Integer, List<Habito>> entrada : habitosPorUsuario.entrySet()) {
            Usuario usuario = usuarios.get(entrada.getKey());
            ZoneId zona = zonaUsuarioService.zonaDe(usuario);
            ZonedDateTime local = ahora.atZone(zona);
            boolean vuelta = CalculoAvisos.esHoraDeVuelta(local);
            if (vuelta || CalculoAvisos.esHoraDeMomentoDebil(local)) {
                candidatos.put(entrada.getKey(),
                        new Candidato(usuario, zona, local.toLocalDate(), entrada.getValue(), vuelta));
            }
        }
        return candidatos;
    }

    /** Los registros solo se leen de quien puede estar retirada, y de él solo desde el tercer aviso. */
    private boolean estaRetirada(Candidato candidato, List<AvisoApp> avisos, Instant ahora) {
        if (!CalculoAvisos.puedeEstarRetirada(avisos, ahora)) {
            return false;
        }
        List<LocalDateTime> completados = registroDAO.creadosCompletadosDesde(
                candidato.usuarioId(), CalculoAvisos.desdeParaRetirada(avisos));
        return CalculoAvisos.retirada(avisos, completados, ahora);
    }

    /**
     * Los semanales a tiro de los candidatos al momento débil, por usuario.
     * Sigue evitándose el N+1: una consulta de registros y otra de historiales
     * por fecha local distinta, no por usuario. Cada hábito se juzga con el
     * objetivo vigente hoy y lo hecho con el de cada día.
     */
    private Map<Integer, List<Habito>> semanalesATiro(List<Candidato> candidatos) {
        Map<LocalDate, List<Candidato>> porFecha = new HashMap<>();
        for (Candidato candidato : candidatos) {
            if (!candidato.esVuelta) {
                porFecha.computeIfAbsent(candidato.hoy, fecha -> new ArrayList<>()).add(candidato);
            }
        }
        Map<Integer, List<Habito>> aTiroPorUsuario = new HashMap<>();
        for (Map.Entry<LocalDate, List<Candidato>> entrada : porFecha.entrySet()) {
            LocalDate hoy = entrada.getKey();
            List<Habito> habitos = entrada.getValue().stream()
                    .flatMap(c -> c.habitos.stream())
                    .collect(Collectors.toList());
            Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(habitos);
            Map<Integer, Hechos> hechos = Hechos.porHabito(
                    registroDAO.findByHabitosAndRango(habitos, hoy.with(java.time.DayOfWeek.MONDAY), hoy),
                    habitos, historiales);
            for (Candidato candidato : entrada.getValue()) {
                aTiroPorUsuario.put(candidato.usuarioId(),
                        CalculoAvisos.semanalesATiro(candidato.habitos, historiales, hechos, hoy));
            }
        }
        return aTiroPorUsuario;
    }

    /**
     * Manda el aviso y lo apunta. Devuelve si salió: con el token dado de baja
     * en FCM no sale, no se apunta (no cuenta para el tope) y se olvida el token.
     */
    private boolean enviar(Candidato candidato, TipoAviso tipo, List<AvisoApp> avisos, String token,
                           List<Habito> nombrados) {
        Usuario usuario = candidato.usuario;
        int variante = CalculoAvisos.variante(tipo, varianteAnterior(avisos, tipo), candidato.usuarioId());
        String clave = claveBase(tipo, nombrados.size()) + "." + variante;

        // El momento débil lleva el nombre (con uno) o cuántos son (con varios)
        // y sus claves se leen siempre con argumentos; las de vuelta, nunca.
        Object[] argumentos = nombrados.isEmpty() ? new Object[0]
                : new Object[]{nombrados.size() == 1 ? nombrados.get(0).getNombre() : nombrados.size()};
        String titulo = textosService.texto(clave + ".titulo", usuario, argumentos);
        String cuerpo = textosService.texto(clave + ".cuerpo", usuario, argumentos);

        boolean tokenInvalido = notificacionService.enviarNotificacion(token, titulo, cuerpo);
        if (tokenInvalido) {
            // Token dado de baja en FCM: lo borramos para no reintentar indefinidamente.
            dispositivoService.olvidarToken(candidato.usuarioId(), AppNorday.HABITOS, token);
            return false;
        }
        avisoAppDAO.save(new AvisoApp(usuario, tipo, variante));
        return true;
    }

    /** La variante del último aviso de ese tipo; 0 si no hay. Los avisos van del más reciente al más antiguo. */
    private static int varianteAnterior(List<AvisoApp> avisos, TipoAviso tipo) {
        for (AvisoApp aviso : avisos) {
            if (aviso.getTipo() == tipo) return aviso.getVariante();
        }
        return 0;
    }

    /** Prefijo de las claves del bundle de un aviso; el momento débil tiene una forma con uno y otra con varios. */
    public static String claveBase(TipoAviso tipo, int nombrados) {
        return switch (tipo) {
            case VUELTA_1 -> "push.aviso.vuelta1";
            case VUELTA_2 -> "push.aviso.vuelta2";
            case MOMENTO_DEBIL -> nombrados == 1 ? "push.aviso.momentodebil.uno" : "push.aviso.momentodebil.varios";
        };
    }

    private record Candidato(Usuario usuario, ZoneId zona, LocalDate hoy, List<Habito> habitos, boolean esVuelta) {
        int usuarioId() {
            return usuario.getUsuarioId();
        }
    }
}
