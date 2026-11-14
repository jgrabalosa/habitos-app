package com.norday.habitos.model;

/**
 * En qué se mide el objetivo de un hábito.
 *
 * VECES: la meta son las veces al día (diario) o los días por semana
 * (semanal), y cada registro es una vez. MINUTOS: cada registro lleva sus
 * minutos en {@code cantidad}, y un día está hecho cuando la suma de los
 * minutos de ese día llega a los minutos objetivo de ese día; la meta son
 * los días por semana (en un diario, siempre 1).
 */
public enum Unidad {
    VECES,
    MINUTOS
}
