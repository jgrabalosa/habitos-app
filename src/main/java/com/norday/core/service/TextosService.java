package com.norday.core.service;

import com.norday.core.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.util.Locale;

/**
 * Resuelve un texto en el idioma del usuario. Único punto por el que pasan
 * los textos que genera el servidor (emails y push).
 *
 * Como ZonaUsuarioService, nunca lanza: si el idioma está corrupto o vacío
 * cae al idioma por defecto. Un dato malo no puede impedir que salga un
 * email de recuperación.
 *
 * Motor: no sabe qué dicen los textos ni de qué módulo vienen.
 */
@Service
public class TextosService {

    @Autowired
    private MessageSource messageSource;

    public Locale localeDe(Usuario usuario) {
        return localeDe(usuario != null ? usuario.getIdioma() : null);
    }

    public Locale localeDe(String idioma) {
        if (idioma == null || idioma.isBlank()) {
            return Locale.forLanguageTag(Usuario.IDIOMA_POR_DEFECTO);
        }
        return Locale.forLanguageTag(idioma.trim());
    }

    public String texto(String clave, Locale locale, Object... args) {
        return messageSource.getMessage(clave, args, locale);
    }

    public String texto(String clave, Usuario usuario, Object... args) {
        return texto(clave, localeDe(usuario), args);
    }
}
