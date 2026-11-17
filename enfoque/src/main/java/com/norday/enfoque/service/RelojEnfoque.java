package com.norday.enfoque.service;

import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * El "ahora" de las sesiones, en un solo sitio para que los tests lo fijen.
 * Es un instante: el día de cada usuario sale de su zona (ZonaUsuarioService).
 */
@Component
public class RelojEnfoque {

    public Instant ahora() {
        return Instant.now();
    }
}
