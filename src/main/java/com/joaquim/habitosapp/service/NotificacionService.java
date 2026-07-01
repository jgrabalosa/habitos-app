package com.joaquim.habitosapp.service;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.stereotype.Service;

@Service
public class NotificacionService {

    public void enviarNotificacion(String fcmToken, String titulo, String cuerpo) {
        if (fcmToken == null || fcmToken.isBlank()) {
            System.out.println("Token FCM vacío, no se envía notificación.");
            return;
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
            System.out.println("Notificación enviada correctamente: " + response);
        } catch (FirebaseMessagingException e) {
            System.err.println("Error al enviar notificación: " + e.getMessage());
        }
    }
}