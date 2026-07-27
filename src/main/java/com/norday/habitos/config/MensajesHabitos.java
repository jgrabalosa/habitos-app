package com.norday.habitos.config;

import com.norday.core.service.ProveedorMensajes;
import org.springframework.stereotype.Component;

/** Textos de esta app: bienvenida y notificaciones push de hábitos. */
@Component
public class MensajesHabitos implements ProveedorMensajes {

    @Override
    public String basename() {
        return "mensajes/habitos";
    }
}
