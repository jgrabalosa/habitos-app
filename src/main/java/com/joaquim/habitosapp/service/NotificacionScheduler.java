package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Frecuencia;
import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Registro;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class NotificacionScheduler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificacionScheduler.class);

    private static final ZoneId ZONA = ZoneId.of("Europe/Madrid");

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private NotificacionService notificacionService;

    /**
     * Barrido cada 5 minutos: notifica los hábitos con recordatorio activo
     * cuya hora elegida cae en esta ventana, que tocan hoy y siguen pendientes.
     */
    @Scheduled(cron = "0 0/5 * * * *", zone = "Europe/Madrid")
    public void enviarRecordatorios() {
        LocalTime ahora = LocalTime.now(ZONA).withSecond(0).withNano(0);
        LocalDate hoy = LocalDate.now(ZONA);
        int diaIsoHoy = hoy.getDayOfWeek().getValue(); // 1=lunes..7=domingo

        List<Habito> activos = habitoDAO.findTodosActivos();

        List<Habito> candidatos = activos.stream()
                .filter(h -> h.isRecordatorioActivo() && h.getRecordatorioHora() != null)
                .filter(h -> redondearArriba5Min(h.getRecordatorioHora()).equals(ahora))
                .filter(h -> tocaHoy(h, diaIsoHoy))
                .collect(Collectors.toList());

        if (candidatos.isEmpty()) {
            return;
        }

        // Una sola consulta para saber qué hábitos ya están completados hoy,
        // en vez de una consulta por hábito (evita N+1).
        Set<Integer> completadosHoy = registroDAO.findByFecha(hoy).stream()
                .filter(Registro::isCompletado)
                .map(r -> r.getHabito().getHabitoId())
                .collect(Collectors.toCollection(HashSet::new));

        int enviados = 0;
        for (Habito habito : candidatos) {
            if (completadosHoy.contains(habito.getHabitoId())) {
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
            log.info("Recordatorios enviados: {} (ventana {})", enviados, ahora);
        }
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