package com.norday.core.config;

import com.norday.core.service.ProveedorMensajes;
import org.springframework.stereotype.Component;

/** Textos del motor de cuenta: recuperación de contraseña. */
@Component
public class MensajesCore implements ProveedorMensajes {

    @Override
    public String basename() {
        return "mensajes/core";
    }
}
