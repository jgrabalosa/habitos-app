package com.norday.core.repository;

public interface IVersionAppDAO {
    /** Versión mínima de la app, o 0 si no tiene fila. */
    int versionMinima(String appId);
}
