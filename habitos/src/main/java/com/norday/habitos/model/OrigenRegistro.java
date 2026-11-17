package com.norday.habitos.model;

/**
 * De dónde viene un registro. MANUAL: lo marca el usuario en la app.
 * ENFOQUE: lo manda Norday Enfoque al terminar una sesión; hoy el backend
 * solo lo acepta y lo guarda, no hace nada distinto con él.
 */
public enum OrigenRegistro {
    MANUAL,
    ENFOQUE
}
