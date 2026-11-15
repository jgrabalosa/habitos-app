package com.norday.enfoque.model;

/**
 * Cómo terminó una sesión de concentración.
 *
 * COMPLETADA: llegó a la duración objetivo. TERMINADA: el usuario la acabó
 * antes, con al menos diez minutos. PERDIDA: se rindió o salió de la app y no
 * volvió a tiempo; se guarda para las estadísticas y nunca suma minutos.
 */
public enum EstadoSesion {
    COMPLETADA,
    TERMINADA,
    PERDIDA;

    /** ¿Cuenta para los minutos, la racha y el aporte al destino? */
    public boolean cuenta() {
        return this != PERDIDA;
    }
}
