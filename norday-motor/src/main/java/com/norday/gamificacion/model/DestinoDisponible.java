package com.norday.gamificacion.model;

/**
 * Un destino al que el usuario puede llevar minutos hoy (ver
 * {@link com.norday.gamificacion.service.DestinoMinutos}): su tipo, su id, el
 * nombre que puso el usuario, los minutos que lleva hoy y los que pide hoy.
 */
public record DestinoDisponible(String tipo, Long id, String nombre, int minutosDia, int minutosObjetivo) {}
