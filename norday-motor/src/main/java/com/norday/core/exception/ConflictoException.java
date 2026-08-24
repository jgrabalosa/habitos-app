package com.norday.core.exception;

/**
 * La operación choca con el estado actual de los datos: algo que ya existe,
 * algo que ya se hizo. Se traduce a 409.
 */
public class ConflictoException extends RuntimeException {
    public ConflictoException(String mensaje) {
        super(mensaje);
    }
}
