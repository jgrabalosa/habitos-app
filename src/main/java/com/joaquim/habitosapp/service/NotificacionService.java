package com.joaquim.habitosapp.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class NotificacionService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(NotificacionService.class);

    /**
     * Envía una notificación push. Devuelve true si el token FCM resultó estar
     * dado de baja (UNREGISTERED), para que el llamador lo borre del usuario y
     * no se siga intentando enviar indefinidamente a un token muerto.
     */
    public boolean enviarNotificacion(String fcmToken, String titulo, String cuerpo) {
        if (fcmToken == null || fcmToken.isBlank()) {
            log.warn("Token FCM vacío, no se envía notificación.");
            return false;
        }

        Notification notification = Notification.builder()
                .setTitle(titulo)
                .setBody(cuerpo)
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .build();

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("Notificación enviada correctamente: {}", response);
            return false;
        } catch (Exception e) {
            // Exception genérica (no solo FirebaseMessagingException): cubre también
            // IllegalStateException si Firebase no llegó a inicializarse. Un fallo
            // aquí nunca debe tumbar el scheduler que llama a este método.
            log.warn("Error al enviar notificación: {}", e.getMessage());
            return e instanceof FirebaseMessagingException fme
                    && fme.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED;
        }
    }
}