package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.NotificacionService;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Sustituye al antiguo RachaSchedulerService.
 *
 * Aquel barrido DECIDÍA: ponía rachas a cero a las 00:05 de Madrid. Eso ya
 * no existe — la rotura es perezosa y vive en el modelo (Racha.sigueViva).
 * Este solo AVISA: si a última hora del día del usuario le queda viva una
 * racha que aún no ha renovado, se le manda un recordatorio.
 *
 * Que este barrido no corra un día ya no corrompe nada: solo significa que
 * ese aviso no se envía. Los datos siguen siendo correctos.
 */
@Component
public class NotificadorRachaEnPeligro {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificadorRachaEnPeligro.class);

    /** Hora local del usuario a la que se avisa. */
    private static final int HORA_AVISO = 21;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private NotificacionService notificacionService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /**
     * Barrido horario. No lleva zona: cada usuario se evalúa contra su propia
     * hora local, y solo se le avisa cuando en su reloj son las 21:00.
     */
    @Scheduled(cron = "0 0 * * * *")
    public void avisarRachasEnPeligro() {
        List<Habito> activos = habitoDAO.findTodosActivos();
        int avisados = 0;

        for (Habito habito : activos) {
            Usuario propietario = habito.getPropietario();
            ZoneId zona = zonaUsuarioService.zonaDe(propietario);

            if (LocalTime.now(zona).getHour() != HORA_AVISO) {
                continue; // en su reloj todavía no toca
            }

            Racha racha = rachaDAO.findByHabito(habito);
            if (racha == null || racha.getRachaActual() == 0) {
                continue; // no hay racha que perder
            }
            if (!racha.sigueViva(zona) || racha.metaAlcanzadaEnPeriodoActual(zona)) {
                continue; // ya rota (nada que salvar), o ya renovada hoy
            }

            String fcmToken = propietario != null ? propietario.getFcmToken() : null;
            if (fcmToken == null || fcmToken.isBlank()) {
                continue;
            }

            notificacionService.enviarNotificacion(
                    fcmToken,
                    "Tu racha de " + racha.getRachaActual() + " está en juego 🔥",
                    "Aún puedes completar \"" + habito.getNombre() + "\" hoy."
            );
            avisados++;
        }

        if (avisados > 0) {
            log.info("Avisos de racha en peligro enviados: {}", avisados);
        }
    }
}
