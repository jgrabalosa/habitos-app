package com.norday.gamificacion.model;

/**
 * Cómo va hoy el usuario con lo suyo, en cuatro escalones. Lo contesta cada
 * app a su manera; el motor solo conoce la respuesta y de ella saca el ánimo
 * de la mascota.
 */
public enum AvanceDeHoy {
    /** Lleva un tiempo sin hacer nada. */
    AUSENTE,
    /** Hoy todavía no ha empezado. */
    SIN_EMPEZAR,
    /** Ya ha dado algún paso. */
    EMPEZADO,
    /** Ha completado todo lo que tocaba hoy. */
    COMPLETO
}
