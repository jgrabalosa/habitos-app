package com.norday.enfoque.model.dto;

/**
 * El cuerpo de POST /api/enfoque/sesiones, tal como llega. Los textos se
 * validan en el servicio, para responder con un 400 traducido en vez del
 * genérico de Jackson.
 *
 * @param clave               UUID que la app genera al empezar la sesión
 * @param inicioUtc           instante de inicio, ISO-8601 (ej. 2026-11-15T09:00:00Z)
 * @param duracionSeg         segundos que duró la sesión
 * @param duracionObjetivoMin minutos que se eligieron al empezar (10–120, de 5 en 5)
 * @param estado              COMPLETADA, TERMINADA o PERDIDA
 * @param destino             a dónde van los minutos; null = sin destino
 */
public record PeticionSesionDTO(String clave, String inicioUtc, Integer duracionSeg, Integer duracionObjetivoMin,
                                String estado, DestinoDTO destino) {

    /** El destino elegido: su tipo (ej. "habito") y su id. */
    public record DestinoDTO(String tipo, Long id) {}
}
