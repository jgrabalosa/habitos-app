package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.TextosService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.CalculoHoy;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
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

    @Autowired
    private TextosService textosService;

    /**
     * Barrido cada 5 minutos: el recordatorio que el usuario ha puesto a cada
     * hábito. Avisa a la hora elegida y solo si toca recordarlo, que lo
     * decide {@link CalculoHoy#tocaRecordar}. Si a un usuario le coinciden
     * varios a la misma hora, recibe un solo aviso que los nombra.
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
        // propio usuario. Se agrupan por la fecha local de su dueño.
        Map<LocalDate, List<Habito>> candidatosPorFecha = new HashMap<>();
        for (Habito habito : activos) {
            if (!habito.isRecordatorioActivo() || habito.getRecordatorioHora() == null) {
                continue;
            }
            ZoneId zona = zonaUsuarioService.zonaDe(habito.getPropietario());
            if (!redondearArriba5Min(habito.getRecordatorioHora()).equals(ventanaActual(zona))) {
                continue;
            }
            candidatosPorFecha.computeIfAbsent(LocalDate.now(zona), fecha -> new ArrayList<>()).add(habito);
        }

        if (candidatosPorFecha.isEmpty()) {
            return;
        }

        // Sigue evitándose el N+1: una consulta por FECHA distinta, no por
        // hábito, con los registros de la semana de esa fecha. En cualquier
        // instante hay como mucho tres fechas locales distintas en el mundo.
        Map<Integer, List<Habito>> pendientesPorUsuario = new LinkedHashMap<>();
        for (Map.Entry<LocalDate, List<Habito>> entrada : candidatosPorFecha.entrySet()) {
            LocalDate hoyUsuario = entrada.getKey();
            List<Habito> candidatos = entrada.getValue();

            Map<Integer, List<LocalDate>> fechasPorHabito = new HashMap<>();
            for (Registro registro : registroDAO.findByHabitosAndRango(
                    candidatos, hoyUsuario.with(DayOfWeek.MONDAY), hoyUsuario)) {
                if (!registro.isCompletado()) continue;
                fechasPorHabito
                        .computeIfAbsent(registro.getHabito().getHabitoId(), id -> new ArrayList<>())
                        .add(registro.getFecha());
            }

            for (Habito habito : candidatos) {
                List<LocalDate> fechas = fechasPorHabito.getOrDefault(habito.getHabitoId(), List.of());
                if (!CalculoHoy.tocaRecordar(habito, fechas, hoyUsuario)) {
                    continue; // hecho, cubierto o con la semana ya cumplida
                }
                Usuario propietario = habito.getPropietario();
                if (propietario == null) {
                    continue;
                }
                pendientesPorUsuario
                        .computeIfAbsent(propietario.getUsuarioId(), id -> new ArrayList<>())
                        .add(habito);
            }
        }

        int enviados = 0;
        for (List<Habito> pendientes : pendientesPorUsuario.values()) {
            Usuario propietario = pendientes.get(0).getPropietario();
            String fcmToken = propietario.getFcmToken();
            if (fcmToken == null || fcmToken.isBlank()) {
                continue;
            }

            // Uno solo: su aviso de siempre. Varios a la misma hora: un único
            // aviso con cuántos son y sus nombres.
            String titulo;
            String cuerpo;
            if (pendientes.size() == 1) {
                titulo = textosService.texto("push.recordatorio.titulo", propietario, pendientes.get(0).getNombre());
                cuerpo = textosService.texto("push.recordatorio.cuerpo", propietario);
            } else {
                String nombres = pendientes.stream().map(Habito::getNombre).collect(Collectors.joining(", "));
                titulo = textosService.texto("push.recordatorio.varios.titulo", propietario, pendientes.size());
                cuerpo = textosService.texto("push.recordatorio.varios.cuerpo", propietario, nombres);
            }

            boolean tokenInvalido = notificacionService.enviarNotificacion(fcmToken, titulo, cuerpo);
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

    // B6 (rediseño de rachas): qué día toca recordar un hábito lo decide ahora
    // CalculoHoy.tocaRecordar, con las mismas reglas que la pantalla de Hoy.
    // La regla anterior se conserva comentada.
    //
    // /** DIARIO, o SEMANAL sin días concretos: toca todos los días.
    //  *  SEMANAL con días concretos: solo los días elegidos. */
    // private boolean tocaHoy(Habito habito, int diaIsoHoy) {
    //     if (habito.getFrecuencia() != Frecuencia.SEMANAL) {
    //         return true;
    //     }
    //     String dias = habito.getDiasSemana();
    //     if (dias == null || dias.isBlank()) {
    //         return true;
    //     }
    //     Set<Integer> diasPlanificados = Arrays.stream(dias.split(","))
    //             .filter(d -> !d.isBlank())
    //             .map(String::trim)
    //             .map(Integer::parseInt)
    //             .collect(Collectors.toSet());
    //     return diasPlanificados.contains(diaIsoHoy);
    // }
}
