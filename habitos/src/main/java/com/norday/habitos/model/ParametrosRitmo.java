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

    private ParametrosRitmo() {}
}
