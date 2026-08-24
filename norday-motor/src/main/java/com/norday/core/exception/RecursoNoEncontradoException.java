package com.norday.core.exception;

/**
 * Un ID que no corresponde a ningún registro existente. Se traduce siempre
 * a 404. El mensaje no llega nunca al usuario (ver MensajesError en
 * norday_flutter_core, que solo mira el código HTTP), así que el texto es
 * solo para depurar.
 */
public class RecursoNoEncontradoException extends RuntimeException {
    public RecursoNoEncontradoException(String mensaje) {
        super(mensaje);
    }
}
