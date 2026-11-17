package com.norday.enfoque.model;

/**
 * Los motivos de rechazo de un aporte que pone este módulo (los del destino
 * los pone su dominio). Son claves del bundle mensajes/enfoque y caben en
 * aporte_motivo (40).
 */
public final class MotivosEnfoque {

    /** La sesión se perdió: nunca lleva minutos a su destino. */
    public static final String SESION_PERDIDA = "enfoque.aporte.sesion.perdida";

    /** Ningún dominio declara ese tipo de destino. */
    public static final String DESTINO_DESCONOCIDO = "enfoque.aporte.destino.desconocido";

    private MotivosEnfoque() {}
}
