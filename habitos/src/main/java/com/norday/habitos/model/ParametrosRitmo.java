package com.norday.habitos.model;

/**
 * Los números del ritmo, en un solo sitio. Son provisionales: se ajustan con
 * uso real, y cambiarlos aquí tiene que bastar.
 */
public final class ParametrosRitmo {

    /** Ritmo con el que empieza un hábito: a mitad de escala, no en cero. */
    public static final double VALOR_INICIAL = 0.5;

    /**
     * Días que tarda el ritmo en recorrer la mitad de lo que le falta para
     * llegar a un periodo mejor que él. Corta: sube rápido.
     */
    public static final double SEMIVIDA_AL_SUBIR_DIAS = 7.0;

    /**
     * Días que tarda el ritmo en perder la mitad de lo que le separa de un
     * periodo peor que él. Tres veces más larga: baja despacio.
     */
    public static final double SEMIVIDA_AL_BAJAR_DIAS = 21.0;

    /**
     * Dónde empieza cada nivel del ritmo. Por debajo de NIVEL_2_DESDE es el
     * nivel 1. Las palabras de cada nivel las pone la app, no el backend.
     */
    public static final double NIVEL_2_DESDE = 0.40;
    public static final double NIVEL_3_DESDE = 0.60;
    public static final double NIVEL_4_DESDE = 0.80;
    public static final double NIVEL_5_DESDE = 0.95;

    /**
     * Días que tarda un hábito nuevo en contar como los demás en el ritmo
     * general. Entra poco a poco para que añadir un hábito no lo baje.
     */
    public static final int DIAS_DE_ENTRADA_EN_EL_GENERAL = 14;

    /**
     * Cuánto tiene que haber cambiado el ritmo en siete días para decir que
     * sube o que baja. No son iguales a propósito: cerca del máximo un único
     * fallo resta hasta 0,032 y no se recupera en la semana, así que con un
     * umbral simétrico un día suelto diría "baja". Con este, un solo fallo
     * en una semana por lo demás completa nunca lo dice.
     */
    public static final double TENDENCIA_SUBE_DESDE = 0.01;
    public static final double TENDENCIA_BAJA_DESDE = 0.045;

    /**
     * Cuántos hábitos hacen falta, todos con buen ritmo a la vez, para el
     * logro que lo premia. Con menos no cuenta.
     */
    public static final int HABITOS_PARA_TODOS_A_UNA = 3;

    private ParametrosRitmo() {}
}
