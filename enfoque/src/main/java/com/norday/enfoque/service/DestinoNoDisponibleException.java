package com.norday.enfoque.service;

/**
 * El destino no ha podido recibir los minutos por un fallo que no es una de
 * sus reglas (la base de datos, un abrazo mortal, un error inesperado). La
 * sesión queda guardada y PENDIENTE: EnfoqueController responde 503 y la app
 * reintenta con la misma clave.
 */
public class DestinoNoDisponibleException extends RuntimeException {

    public DestinoNoDisponibleException(Throwable causa) {
        super(causa);
    }
}
