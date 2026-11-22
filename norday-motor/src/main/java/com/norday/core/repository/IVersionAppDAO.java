package com.norday.core.repository;

public interface IVersionAppDAO {

    /**
     * Las dos versiones de una app, leídas de una sola fila.
     *
     * @param minima      versionCode por debajo del cual la app se bloquea
     * @param recomendada versionCode por debajo del cual la app avisa sin
     *                    obligar; nulo = sin aviso
     */
    record Versiones(int minima, Integer recomendada) {}

    /** Mínima 0 y recomendada nula si la app no tiene fila. */
    Versiones versiones(String appId);
}
