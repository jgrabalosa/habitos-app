package com.norday.gamificacion.service;

import com.norday.gamificacion.model.AvanceDeHoy;

import java.util.List;

/**
 * Cómo sabe el motor si el usuario ya cumplió hoy lo suyo. El motor no sabe
 * qué es "lo suyo" —hábitos completados, píldoras leídas, lo que cada app
 * decida—: sólo pregunta, y pregunta en el momento.
 *
 * Se pregunta en vez de guardarse a propósito. Un sello guardado hay que
 * acordarse de revertirlo en cada camino que invalide el día (borrar un
 * hábito, desactivarlo, añadir otro, subir una meta), y basta que uno se
 * olvide para que la mascota mienta.
 *
 * Cada app del ecosistema que mueve el ánimo de la mascota implementa el
 * puerto, y CumplimientoCombinado junta las respuestas de las que el usuario
 * usa (16-nov-2026).
 */
public interface CumplimientoDiarioPort {
    boolean hoyCumplido(int usuarioId);

    /**
     * Si el usuario usa esta app: solo cuentan para el ánimo las apps en uso,
     * para que una app que no ha abierto nunca no le ponga triste a la
     * mascota. Cada app decide qué es usarla.
     */
    boolean enUso(int usuarioId);

    /**
     * Cómo va hoy el usuario con lo suyo, en cuatro escalones: de llevar un
     * tiempo sin aparecer a tenerlo todo completo. De aquí sale el ánimo de
     * la mascota. Igual que arriba, se pregunta en el momento y no se guarda.
     */
    AvanceDeHoy comoVaHoy(int usuarioId);

    /**
     * Cómo irá el usuario cada uno de los próximos {@code dias} días si no
     * hace nada más: la posición 0 es mañana, la 1 pasado mañana, y así hasta
     * {@code dias} elementos. «Mañana» es el del usuario, en su zona. Cada
     * elemento es lo que {@link #comoVaHoy} contestaría ese día con los datos
     * de ahora; las dos sirven la misma regla. Es lo que necesita quien pinta
     * a la mascota sin abrir la app. Con {@code dias} menor o igual que 0, la
     * lista vacía.
     *
     * Es abstracto a propósito: un default devolvería una respuesta inventada
     * para una app que no ha pensado en ello. Una lista de la misma longitud
     * por app permite combinarlas día a día.
     */
    List<AvanceDeHoy> comoVaLosProximosDias(int usuarioId, int dias);
}
