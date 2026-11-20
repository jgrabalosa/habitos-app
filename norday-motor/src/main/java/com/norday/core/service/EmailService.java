package com.norday.core.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.InternetAddress;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.Resource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.Map;

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
     * Remitente visible del ecosistema. No tiene por qué coincidir con la
     * cuenta SMTP que autentica el envío: basta con que esté verificada en
     * ella como alias "Enviar como".
     */
    @Value("${norday.mail.from}")
    private String remitente;

    /**
     * @param claveAsunto clave del bundle para el asunto
     * @param claveCuerpo clave del bundle para el cuerpo
     * @param args        parámetros del cuerpo, en orden ({0}, {1}...)
     */
    public void enviarEmail(String destinatario, String claveAsunto, String claveCuerpo,
                            Locale locale, Object... args) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(remitente);
        mensaje.setTo(destinatario);
        mensaje.setSubject(textosService.texto(claveAsunto, locale));
        mensaje.setText(textosService.texto(claveCuerpo, locale, args));
        mailSender.send(mensaje);
    }

    /**
     * Correo en HTML con versión de texto plano e imágenes dentro del propio
     * correo (multipart/related con Content-ID; nunca en base64 ni por URL).
     * Estructura: related { alternative { text/plain, text/html }, imágenes }.
     * El motor no sabe qué dicen los textos: llegan ya resueltos.
     *
     * @param nombreRemitente nombre visible; la dirección es siempre la misma
     * @param imagenes        identificador (el de {@code cid:}) → recurso
     */
    public void enviarHtml(String destinatario, String asunto, String html, String texto,
                           String nombreRemitente, Map<String, ? extends Resource> imagenes)
            throws MessagingException, UnsupportedEncodingException {
        MimeMessage mensaje = mailSender.createMimeMessage();
        MimeMessageHelper helper =
                new MimeMessageHelper(mensaje, MimeMessageHelper.MULTIPART_MODE_RELATED, "UTF-8");
        helper.setFrom(new InternetAddress(remitente, nombreRemitente, "UTF-8"));
        helper.setTo(destinatario);
        helper.setSubject(asunto);
        helper.setText(texto, html);
        for (Map.Entry<String, ? extends Resource> imagen : imagenes.entrySet()) {
            helper.addInline(imagen.getKey(), imagen.getValue(), "image/png");
        }
        mailSender.send(mensaje);
    }
}
