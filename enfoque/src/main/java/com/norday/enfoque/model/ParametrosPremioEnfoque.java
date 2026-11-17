package com.norday.enfoque.model;

/**
 * Los números del premio de Norday Focus, en un solo sitio (decisión del
 * 16-nov-2026, docs/decisiones/2026-11-16-focus-paga-lo-suyo.md).
 *
 * Calibrados contra Norday Habits: un día de hábito bien hecho son 25 monedas
 * y 5 XP, y una sesión de 25 minutos completada son 30 monedas y 5 XP. Con
 * dos sesiones al día, una identidad (1000) cuesta unas dos semanas, como con
 * tres hábitos.
 */
public final class ParametrosPremioEnfoque {

    /** Monedas por cada minuto entero de una sesión que cuenta. */
    public static final int MONEDAS_POR_MINUTO = 1;

    /**
     * Lo que añade llegar al final (COMPLETADA). Una TERMINADA no lo lleva:
     * acabar antes nunca sale más a cuenta que completar.
     */
    public static final int REMATE_COMPLETADA = 5;

    /** Una XP de Nori por cada tantos minutos contados, redondeando hacia abajo. */
    public static final int MINUTOS_POR_XP = 5;

    /**
     * Lo más que pagan las sesiones en un día del usuario (unas cuatro de 25
     * minutos). Pasado el tope, la sesión cuenta igual para las estadísticas,
     * la racha y los logros, pero no paga. No toca lo que paga el hábito
     * destino ni lo que pagan los logros.
     */
    public static final int TOPE_MONEDAS_DIA = 120;

    public static final int TOPE_XP_DIA = 24;

    /**
     * Minutos que hacen un «día de enfoque»: el logro ENFOQUE_DIA y el día
     * completo de Focus para el ánimo de Nori. Fijo en la v1.
     */
    public static final int MINUTOS_DIA_DE_ENFOQUE = 50;

    /**
     * Días seguidos sin ninguna sesión a partir de los cuales Focus cuenta
     * como ausente para el ánimo de Nori. Es el mismo número que en Norday
     * Habits (ParametrosPremio.DIAS_SIN_HACER_PARA_VOLVER), que no se puede
     * importar: los dominios no se conocen.
     */
    public static final int DIAS_SIN_SESION_PARA_AUSENTE = 3;

    /** Días hacia atrás (hoy incluido) en que una sesión hace que Focus esté «en uso». */
    public static final int DIAS_EN_USO = 14;

    private ParametrosPremioEnfoque() {}

    /** Las monedas de una sesión antes del tope: 0 si no cuenta. */
    public static int monedasDe(EstadoSesion estado, int minutos) {
        if (estado == null || !estado.cuenta()) return 0;
        return minutos * MONEDAS_POR_MINUTO + (estado == EstadoSesion.COMPLETADA ? REMATE_COMPLETADA : 0);
    }

    /** La XP de una sesión antes del tope: 0 si no cuenta. */
    public static int xpDe(EstadoSesion estado, int minutos) {
        if (estado == null || !estado.cuenta()) return 0;
        return minutos / MINUTOS_POR_XP;
    }
}
