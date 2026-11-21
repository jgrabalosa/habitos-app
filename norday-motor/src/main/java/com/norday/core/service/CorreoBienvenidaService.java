package com.norday.core.service;

import com.norday.core.model.Usuario;
import jakarta.mail.MessagingException;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.UnsupportedEncodingException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Monta y envía el correo de bienvenida: la plantilla HTML
 * ({@code correo/bienvenida.html}) con sus marcadores {@code {{...}}}
 * resueltos con las claves {@code email.bienvenida.*} del bundle, en el idioma
 * del usuario.
 *
 * Motor: no contiene ni una frase. Textos, enlaces e imágenes viven en el
 * bundle y en los recursos del classpath.
 *
 * Sin motor de plantillas: sustitución de marcadores. Todo valor que entra en
 * el HTML se escapa, y el nombre el primero, porque lo escribe el usuario.
 */
@Service
public class CorreoBienvenidaService {

    static final String PLANTILLA = "correo/bienvenida.html";

    /** Identificador de {@code cid:} → recurso del classpath. */
    static final Map<String, String> IMAGENES = Map.of(
            "nori", "correo/img/nori.png",
            "instagram", "correo/img/instagram.png",
            "tiktok", "correo/img/tiktok.png");

    private static final Pattern MARCADOR = Pattern.compile("\\{\\{(\\w+)}}");

    private final EmailService emailService;
    private final TextosService textosService;

    public CorreoBienvenidaService(EmailService emailService, TextosService textosService) {
        this.emailService = emailService;
        this.textosService = textosService;
    }

    public void enviar(Usuario usuario) throws MessagingException, UnsupportedEncodingException {
        Locale locale = textosService.localeDe(usuario);
        Map<String, Resource> imagenes = new LinkedHashMap<>();
        IMAGENES.forEach((cid, ruta) -> imagenes.put(cid, new ClassPathResource(ruta)));
        emailService.enviarHtml(
                usuario.getEmail(),
                textosService.texto("email.bienvenida.asunto", locale),
                html(usuario),
                texto(usuario),
                textosService.texto("email.bienvenida.remitente", locale),
                imagenes);
    }

    public String html(Usuario usuario) {
        Locale locale = textosService.localeDe(usuario);
        Map<String, String> valores = new LinkedHashMap<>();
        valores.put("idioma", etiquetaIdioma(locale));
        valores.put("asunto", t("asunto", locale));
        valores.put("vistaPrevia", t("vistaPrevia", locale));
        valores.put("altNori", t("altNori", locale));
        valores.put("titular", titular(usuario, locale));
        valores.put("parrafo1", t("parrafo1", locale));
        valores.put("parrafo2", t("parrafo2", locale));
        valores.put("parrafo3", t("parrafo3", locale));
        valores.put("boton", t("boton", locale));
        valores.put("firma", t("firma", locale));
        valores.put("redes", t("redes", locale));
        valores.put("pie", t("pie", locale));
        valores.put("enlacePlay", t("enlace.play", locale));
        valores.put("enlaceInstagram", t("enlace.instagram", locale));
        valores.put("enlaceTikTok", t("enlace.tiktok", locale));

        String plantilla = leerPlantilla();
        StringBuilder salida = new StringBuilder(plantilla.length() + 1024);
        Matcher m = MARCADOR.matcher(plantilla);
        while (m.find()) {
            String valor = valores.get(m.group(1));
            if (valor == null) {
                throw new IllegalStateException("Marcador sin valor en " + PLANTILLA + ": " + m.group(1));
            }
            m.appendReplacement(salida, Matcher.quoteReplacement(escapar(valor)));
        }
        m.appendTail(salida);
        return salida.toString();
    }

    public String texto(Usuario usuario) {
        Locale locale = textosService.localeDe(usuario);
        return String.join("\n\n",
                titular(usuario, locale),
                t("parrafo1", locale),
                t("parrafo2", locale),
                t("parrafo3", locale),
                t("boton", locale) + ": " + t("enlace.play", locale),
                t("firma", locale),
                t("redes", locale) + "\nInstagram: " + t("enlace.instagram", locale)
                        + "\nTikTok: " + t("enlace.tiktok", locale),
                t("pie", locale)) + "\n";
    }

    private String t(String clave, Locale locale) {
        // Sin argumentos: Spring no pasa el texto por MessageFormat y el
        // apóstrofo simple sale tal cual.
        return textosService.texto("email.bienvenida." + clave, locale);
    }

    private String titular(Usuario usuario, Locale locale) {
        String nombre = primerNombre(usuario.getNombre());
        if (nombre.isEmpty()) {
            return t("titularSinNombre", locale);
        }
        // Con argumento sí pasa por MessageFormat: ahí el apóstrofo va doblado.
        return textosService.texto("email.bienvenida.titular", locale, nombre);
    }

    /** Primera palabra del nombre, sin espacios alrededor; vacía si no hay. */
    static String primerNombre(String nombre) {
        if (nombre == null) {
            return "";
        }
        String limpio = nombre.strip();
        int espacio = limpio.indexOf(' ');
        return espacio < 0 ? limpio : limpio.substring(0, espacio);
    }

    /** es, en o pt-PT: el portugués del producto es el europeo. */
    private static String etiquetaIdioma(Locale locale) {
        return switch (locale.getLanguage()) {
            case "en" -> "en";
            case "pt" -> "pt-PT";
            default -> "es";
        };
    }

    static String escapar(String valor) {
        StringBuilder sb = new StringBuilder(valor.length() + 16);
        for (char c : valor.toCharArray()) {
            switch (c) {
                case '&' -> sb.append("&amp;");
                case '<' -> sb.append("&lt;");
                case '>' -> sb.append("&gt;");
                case '"' -> sb.append("&quot;");
                case '\'' -> sb.append("&#39;");
                default -> sb.append(c);
            }
        }
        return sb.toString();
    }

    private static String leerPlantilla() {
        try {
            return new String(new ClassPathResource(PLANTILLA).getInputStream().readAllBytes(),
                    StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("No se pudo leer " + PLANTILLA, e);
        }
    }
}
