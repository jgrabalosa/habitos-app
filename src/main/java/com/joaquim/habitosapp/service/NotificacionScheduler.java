package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class NotificacionScheduler {

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private NotificacionService notificacionService;

    @Scheduled(cron = "0 37 15 * * *", zone = "Europe/Madrid")
    public void enviarRecordatorioDiario() {
        System.out.println("Ejecutando recordatorio diario de hábitos...");

        List<Usuario> usuarios = usuarioDAO.findAll();
        for (Usuario usuario : usuarios) {
            if (usuario.getFcmToken() != null && !usuario.getFcmToken().isBlank()) {
                notificacionService.enviarNotificacion(
                        usuario.getFcmToken(),
                        "¡No olvides tus hábitos! 🎯",
                        "Tómate un momento para completar tus hábitos de hoy."
                );
            }
        }

        System.out.println("Recordatorio diario finalizado. Usuarios notificados: " + usuarios.size());
    }
}