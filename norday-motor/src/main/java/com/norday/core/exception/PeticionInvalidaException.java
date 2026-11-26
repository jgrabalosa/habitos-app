package com.norday.core.exception;

/**
 * Rechazo esperado de una petición que no se puede atender tal cual está (por
 * ejemplo, falta saldo). Se traduce a 400 y se apunta como WARN, sin traza:
 * no es un fallo del servidor.
 */
public class PeticionInvalidaException extends RuntimeException {
    public PeticionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
