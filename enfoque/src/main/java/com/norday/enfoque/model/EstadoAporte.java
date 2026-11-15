package com.norday.enfoque.model;

/**
 * Qué pasó con los minutos de una sesión en su destino.
 *
 * SIN_DESTINO: la sesión no iba a ningún sitio. PENDIENTE: guardada, pero
 * todavía no se ha llevado al destino (o se cortó antes de apuntar el
 * resultado: el reintento lo completa). ACEPTADO, REPETIDO y RECHAZADO son
 * los de {@link com.norday.gamificacion.model.ResultadoAporte}.
 */
public enum EstadoAporte {
    SIN_DESTINO,
    PENDIENTE,
    ACEPTADO,
    REPETIDO,
    RECHAZADO;

    /** ¿Están ya los minutos en el destino? */
    public boolean minutosEnDestino() {
        return this == ACEPTADO || this == REPETIDO;
    }
}
