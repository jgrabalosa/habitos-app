package com.norday.enfoque.config;

import com.norday.core.service.ProveedorMensajes;
import org.springframework.stereotype.Component;

/** Textos de Norday Focus: los errores de las sesiones y sus motivos de rechazo. */
@Component
public class MensajesEnfoque implements ProveedorMensajes {

    @Override
    public String basename() {
        return "mensajes/enfoque";
    }
}
