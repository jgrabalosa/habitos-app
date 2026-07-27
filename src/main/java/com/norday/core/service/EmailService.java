package com.norday.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Envía emails. El motor pone el mecanismo; el texto viene siempre del
 * bundle del módulo que corresponda, resuelto en el idioma del destinatario.
 *
 * Esta clase no contiene ni una frase de cara al usuario a propósito: antes
 * el email de bienvenida hablaba de "construir mejores hábitos" desde dentro
 * del motor, que es contenido de una app concreta colado en código que
 * comparte todo el ecosistema.
 */
@Service
public class EmailService {

    @Autowired
    private JavaMailSender mailSender;

    @Autowired
    private TextosService textosService;

    /**
     * @param claveAsunto clave del bundle para el asunto
     * @param claveCuerpo clave del bundle para el cuerpo
     * @param args        parámetros del cuerpo, en orden ({0}, {1}...)
     */
    public void enviarEmail(String destinatario, String claveAsunto, String claveCuerpo,
                            Locale locale, Object... args) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setTo(destinatario);
        mensaje.setSubject(textosService.texto(claveAsunto, locale));
        mensaje.setText(textosService.texto(claveCuerpo, locale, args));
        mailSender.send(mensaje);
    }
}
