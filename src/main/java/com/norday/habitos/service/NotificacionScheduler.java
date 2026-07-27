package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NotificacionScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificacionScheduler.class);

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /**
     * Barrido cada 5 minutos: notifica los hábitos con recordatorio activo
     * cuya hora elegida cae en esta ventana, que tocan hoy y siguen pendientes.
     *
     * La hora del hábito se compara contra la hora local del DUEÑO, no contra
     * la del servidor: alguien en Sao Paulo con recordatorio a las 08:00 lo
     * recibe a sus 08:00. El cron no lleva zona porque cada 5 minutos es cada
     * 5 minutos en cualquier zona; quien decide es la comparación de dentro.
     */
    @Scheduled(cron = "0 0/5 * * * *")
    public void enviarRecordatorios() {
        List<Habito> activos = habitoDAO.findTodosActivos();

        // Candidatos: la hora del recordatorio cae en la ventana actual del
        // propio usuario y el hábito toca hoy según SU calendario.
        List<Habito> candidatos = new ArrayList<>();
        Map<Integer, LocalDate> hoyPorHabito = new HashMap<>();

        for (Habito habito : activos) {
            if (!habito.isRecordatorioActivo() || habito.getRecordatorioHora() == null) {
                continue;
            }
            ZoneId zona = zonaUsuarioService.zonaDe(habito.getPropietario());
            if (!redondearArriba5Min(habito.getRecordatorioHora()).equals(ventanaActual(zona))) {
                continue;
            }
            LocalDate hoyUsuario = LocalDate.now(zona);
            if (!tocaHoy(habito, hoyUsuario.getDayOfWeek().getValue())) {
                continue;
            }
            candidatos.add(habito);
            hoyPorHabito.put(habito.getHabitoId(), hoyUsuario);
        }

        if (candidatos.isEmpty()) {
            return;
        }

        // Sigue evitándose el N+1: una consulta por FECHA distinta, no por
        // hábito. En cualquier instante hay como mucho tres fechas locales
        // distintas en el mundo.
        Map<LocalDate, Set<Integer>> completadosPorFecha = new HashMap<>();
        for (LocalDate fecha : new HashSet<>(hoyPorHabito.values())) {
            completadosPorFecha.put(fecha, registroDAO.findByFecha(fecha).stream()
                    .filter(Registro::isCompletado)
                    .map(r -> r.getHabito().getHabitoId())
                    .collect(Collectors.toCollection(HashSet::new)));
        }

        int enviados = 0;
        for (Habito habito : candidatos) {
            LocalDate hoyUsuario = hoyPorHabito.get(habito.getHabitoId());
            if (completadosPorFecha.get(hoyUsuario).contains(habito.getHabitoId())) {
                continue; // ya completado hoy, no está pendiente
            }
            Usuario propietario = habito.getPropietario();
            String fcmToken = propietario != null ? propietario.getFcmToken() : null;
            if (fcmToken == null || fcmToken.isBlank()) {
                continue;
            }
            boolean tokenInvalido = notificacionService.enviarNotificacion(
                    fcmToken,
                    "¡No olvides \"" + habito.getNombre() + "\"! 🎯",
                    "Tómate un momento para completarlo hoy."
            );
            if (tokenInvalido) {
                // Token dado de baja en FCM: lo borramos para no reintentar indefinidamente.
                propietario.setFcmToken(null);
                usuarioDAO.update(propietario);
            } else {
                enviados++;
            }
        }

        if (enviados > 0) {
            log.info("Recordatorios enviados: {}", enviados);
        }
    }

    /**
     * Ventana de 5 minutos en curso para esa zona, anclada hacia ABAJO.
     *
     * No se compara contra el minuto exacto: si la ejecución se retrasa unos
     * segundos por encima del minuto (GC, arranque en frío, cola del
     * scheduler), comparar el minuto exacto haría perder en silencio todos
     * los recordatorios de esa ventana.
     */
    private LocalTime ventanaActual(ZoneId zona) {
        LocalTime ahora = LocalTime.now(zona).withSecond(0).withNano(0);
        return ahora.withMinute((ahora.getMinute() / 5) * 5);
    }

    /** Redondea hacia ARRIBA al siguiente múltiplo de 5 minutos (nunca antes de la hora elegida). */
    private LocalTime redondearArriba5Min(LocalTime hora) {
        LocalTime base = hora.withSecond(0).withNano(0);
        int resto = base.getMinute() % 5;
        return resto == 0 ? base : base.plusMinutes(5 - resto);
    }

    /** DIARIO, o SEMANAL sin días concretos: toca todos los días.
     *  SEMANAL con días concretos: solo los días elegidos. */
    private boolean tocaHoy(Habito habito, int diaIsoHoy) {
        if (habito.getFrecuencia() != Frecuencia.SEMANAL) {
            return true;
        }
        String dias = habito.getDiasSemana();
        if (dias == null || dias.isBlank()) {
            return true;
        }
        Set<Integer> diasPlanificados = Arrays.stream(dias.split(","))
                .filter(d -> !d.isBlank())
                .map(String::trim)
                .map(Integer::parseInt)
                .collect(Collectors.toSet());
        return diasPlanificados.contains(diaIsoHoy);
    }
}
