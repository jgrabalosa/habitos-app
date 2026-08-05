package com.norday.core.service;

import com.norday.core.model.Usuario;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;

/**
 * Resuelve la zona horaria efectiva de un usuario. Única puerta de entrada
 * para saber qué día es "hoy" para alguien.
 *
 * Nunca lanza: si el valor guardado está corrupto, vacío o es una zona que
 * este JDK no conoce, cae a la zona por defecto y sigue. Un dato malo no
 * puede tumbar un scheduler ni impedir que alguien complete un hábito.
 *
 * Motor: no conoce ningún concepto de dominio.
 */
@Service
public class ZonaUsuarioService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ZonaUsuarioService.class);

    private static final ZoneId ZONA_POR_DEFECTO = ZoneId.of(Usuario.ZONA_POR_DEFECTO);

    public ZoneId zonaDe(Usuario usuario) {
        if (usuario == null) {
            return ZONA_POR_DEFECTO;
        }
        return zonaDe(usuario.getZonaHoraria());
    }

    public ZoneId zonaDe(String zonaHoraria) {
        if (zonaHoraria == null || zonaHoraria.isBlank()) {
            return ZONA_POR_DEFECTO;
        }
        try {
            return ZoneId.of(zonaHoraria.trim());
        } catch (Exception e) {
            log.warn("Zona horaria no reconocida ({}), se usa {}", zonaHoraria, ZONA_POR_DEFECTO);
            return ZONA_POR_DEFECTO;
        }
    }

    /** El "hoy" del usuario, que no tiene por qué ser el del servidor. */
    public LocalDate hoyDe(Usuario usuario) {
        return LocalDate.now(zonaDe(usuario));
    }
}
