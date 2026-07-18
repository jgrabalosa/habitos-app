package com.joaquim.habitosapp.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    public void enviarEmailBienvenida(String destinatario, String nombre) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(destinatario);
        mensaje.setSubject("¡Bienvenido a Norday! 🎉");
        mensaje.setText(
                "Hola " + nombre + ",\n\n" +
                        "¡Gracias por unirte a Norday! Estamos encantados de acompañarte " +
                        "en tu camino para construir mejores hábitos.\n\n" +
                        "Empieza creando tu primer hábito y da el primer paso hacia una mejor versión de ti mismo.\n\n" +
                        "Un saludo,\n" +
                        "El equipo de Norday"
        );
        mailSender.send(mensaje);
    }

    public void enviarEmailRecuperacion(String destinatario, String codigo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(destinatario);
        mensaje.setSubject("Tu código de recuperación de Norday");
        mensaje.setText(
                "Hola,\n\n" +
                        "Hemos recibido una solicitud para restablecer tu contraseña.\n\n" +
                        "Tu código de recuperación es: " + codigo + "\n\n" +
                        "Este código caduca en 15 minutos. Si no has solicitado este cambio, " +
                        "puedes ignorar este mensaje: tu contraseña seguirá siendo la misma.\n\n" +
                        "Un saludo,\n" +
                        "El equipo de Norday"
        );
        mailSender.send(mensaje);
    }
}
