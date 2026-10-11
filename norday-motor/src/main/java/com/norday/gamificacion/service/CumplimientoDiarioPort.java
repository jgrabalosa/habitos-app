package com.norday.gamificacion.service;

import com.norday.gamificacion.model.AvanceDeHoy;

/**
 * Cómo sabe el motor si el usuario ya cumplió hoy lo suyo. El motor no sabe
 * qué es "lo suyo" —hábitos completados, píldoras leídas, lo que cada app
 * decida—: sólo pregunta, y pregunta en el momento.
 *
 * Se pregunta en vez de guardarse a propósito. Un sello guardado hay que
 * acordarse de revertirlo en cada camino que invalide el día (borrar un
 * hábito, desactivarlo, añadir otro, subir una meta), y basta que uno se
 * olvide para que la mascota mienta.
 */
public interface CumplimientoDiarioPort {
    boolean hoyCumplido(int usuarioId);

    /**
     * Cómo va hoy el usuario con lo suyo, en cuatro escalones: de llevar un
     * tiempo sin aparecer a tenerlo todo completo. De aquí sale el ánimo de
     * la mascota. Igual que arriba, se pregunta en el momento y no se guarda.
     */
    AvanceDeHoy comoVaHoy(int usuarioId);
}
