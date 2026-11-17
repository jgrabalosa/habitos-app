package com.norday.gamificacion.model;

import java.util.List;

/**
 * Lo que ha pasado al llevar minutos a un destino (ver
 * {@link com.norday.gamificacion.service.DestinoMinutos#aportar}).
 *
 * <ul>
 *   <li>{@code estado}: ACEPTADO (sumado y pagado con las reglas del destino),
 *       REPETIDO (la clave ya se había usado: nada nuevo) o RECHAZADO.</li>
 *   <li>{@code motivo}: solo en RECHAZADO; una clave de los textos traducidos.</li>
 *   <li>{@code monedas} y {@code xp}: lo que ha pagado este aporte; 0 si no es
 *       ACEPTADO.</li>
 *   <li>{@code minutosDia} y {@code minutosObjetivo}: cómo queda el día del
 *       destino; null si no se sabe (RECHAZADO).</li>
 *   <li>{@code diaHecho}: si el día del destino cuenta como hecho; null si no
 *       se sabe.</li>
 *   <li>{@code logros}: los códigos de los logros desbloqueados por este aporte.</li>
 * </ul>
 */
public record ResultadoAporte(Estado estado, String motivo, int monedas, int xp, Integer minutosDia,
                              Integer minutosObjetivo, Boolean diaHecho, List<String> logros) {

    public enum Estado {
        ACEPTADO,
        REPETIDO,
        RECHAZADO
    }

    public ResultadoAporte {
        logros = logros == null ? List.of() : List.copyOf(logros);
    }

    /** Un rechazo, con su código de motivo y nada más. */
    public static ResultadoAporte rechazado(String motivo) {
        return new ResultadoAporte(Estado.RECHAZADO, motivo, 0, 0, null, null, null, List.of());
    }
}
