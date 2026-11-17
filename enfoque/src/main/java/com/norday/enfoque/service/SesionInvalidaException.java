package com.norday.enfoque.service;

/**
 * Una petición de Norday Focus que no cumple las reglas: 400 con el texto
 * traducido. Hereda de IllegalArgumentException, que es como el resto del
 * backend señala un 400, pero tiene su propio manejador en EnfoqueController
 * para no ensuciar el log con un error que es del cliente.
 */
public class SesionInvalidaException extends IllegalArgumentException {

    public SesionInvalidaException(String mensaje) {
        super(mensaje);
    }
}
