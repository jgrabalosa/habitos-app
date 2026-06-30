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
        mensaje.setSubject("¡Bienvenido a HábitosApp! 🎉");
        mensaje.setText(
                "Hola " + nombre + ",\n\n" +
                        "¡Gracias por unirte a HábitosApp! Estamos encantados de acompañarte " +
                        "en tu camino para construir mejores hábitos.\n\n" +
                        "Empieza creando tu primer hábito y da el primer paso hacia una mejor versión de ti mismo.\n\n" +
                        "Un saludo,\n" +
                        "El equipo de HábitosApp"
        );
        mailSender.send(mensaje);
    }
}