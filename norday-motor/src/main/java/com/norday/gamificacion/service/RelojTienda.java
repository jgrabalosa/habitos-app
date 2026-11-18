package com.norday.gamificacion.service;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * El "ahora" de la tienda (las ofertas por periodo), en un solo sitio para
 * que los tests lo fijen. Es un instante: el día de la compra sale de la zona
 * del usuario (ZonaUsuarioService) y el del catálogo, de UTC.
 */
@Component
public class RelojTienda {

    public Instant ahora() {
        return Instant.now();
    }
}
