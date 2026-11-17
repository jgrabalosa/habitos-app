package com.norday.gamificacion.model;

/**
 * Cómo va un día de un destino, leído en el momento (ver
 * {@link com.norday.gamificacion.service.DestinoMinutos#progreso}): los
 * minutos que lleva, los que pide y si cuenta como hecho. Cualquiera puede ser
 * null si ese día el destino no se mide en minutos.
 */
public record ProgresoDestino(Integer minutosDia, Integer minutosObjetivo, Boolean diaHecho) {}
