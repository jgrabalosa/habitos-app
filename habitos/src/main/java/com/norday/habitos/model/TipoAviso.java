package com.norday.habitos.model;

/**
 * Los avisos que la app manda por iniciativa propia: dos de vuelta (el primero
 * y el segundo tras un tiempo sin entrar) y uno de momento débil. Nunca uno por
 * hábito. Se guarda por nombre en {@code aviso_app.tipo}: no renombrar.
 */
public enum TipoAviso {
    VUELTA_1,
    VUELTA_2,
    MOMENTO_DEBIL
}
