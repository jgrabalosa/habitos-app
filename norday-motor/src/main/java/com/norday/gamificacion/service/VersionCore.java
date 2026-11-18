package com.norday.gamificacion.service;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * La versión del core de Flutter que manda la app en la cabecera
 * X-Norday-Core («0.56.0»), y su comparación con la versión mínima de un
 * producto. Solo se aceptan tres números separados por puntos: con sufijo
 * («0.56.0+1», «0.56.0-beta») o sin cabecera, la versión es ilegible y un
 * producto con versión mínima no se ve ni se vende.
 */
public final class VersionCore {

    private static final Pattern FORMA = Pattern.compile("(\\d{1,6})\\.(\\d{1,6})\\.(\\d{1,6})");

    private VersionCore() {}

    /** Los tres números, o null si no se puede leer. */
    static int[] leer(String version) {
        if (version == null) {
            return null;
        }
        Matcher m = FORMA.matcher(version.trim());
        if (!m.matches()) {
            return null;
        }
        return new int[]{Integer.parseInt(m.group(1)), Integer.parseInt(m.group(2)), Integer.parseInt(m.group(3))};
    }

    /**
     * Si un cliente con el core [cabecera] puede ver un producto que pide
     * [minima]. Sin mínima, siempre. Con mínima, solo si la cabecera se lee y
     * es igual o mayor, número a número (0.10.0 es mayor que 0.9.0). Una
     * mínima ilegible tampoco deja ver el producto: mejor oculto que vendido a
     * quien no lo puede pintar.
     */
    public static boolean alcanza(String cabecera, String minima) {
        if (minima == null || minima.isBlank()) {
            return true;
        }
        int[] tiene = leer(cabecera);
        int[] pide = leer(minima);
        if (tiene == null || pide == null) {
            return false;
        }
        for (int i = 0; i < 3; i++) {
            if (tiene[i] != pide[i]) {
                return tiene[i] > pide[i];
            }
        }
        return true;
    }
}
