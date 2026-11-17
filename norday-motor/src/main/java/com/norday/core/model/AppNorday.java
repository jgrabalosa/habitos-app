package com.norday.core.model;

import java.util.Set;

/**
 * Las apps del ecosistema que el backend conoce, por el appId que cada una
 * fija en el core de Flutter (ApiServiceCore.appId) y envía en la cabecera
 * X-Norday-App.
 *
 * Es una lista cerrada a propósito: el appId decide a qué token de
 * notificaciones va cada aviso, y un valor desconocido aceptado en silencio
 * acabaría guardando un token donde nadie lo lee, o pisando el de otra app.
 * Una app nueva se añade aquí antes de publicarse.
 */
public final class AppNorday {

    public static final String HABITOS = "habitos";
    public static final String CONOCIMIENTO = "conocimiento";
    public static final String ENFOQUE = "enfoque";

    public static final Set<String> CONOCIDAS = Set.of(HABITOS, CONOCIMIENTO, ENFOQUE);

    /**
     * La app que se supone cuando falta la cabecera. La APK de Norday Habits
     * ya instalada registra su token sin cabecera, y no puede quedarse sin
     * recordatorios por eso.
     */
    public static final String SIN_CABECERA = HABITOS;

    private AppNorday() {}

    /**
     * El appId que corresponde a una cabecera X-Norday-App: sin cabecera o
     * vacía, {@link #SIN_CABECERA}; una app conocida, ella misma; cualquier
     * otra cosa, null, para que quien llama la rechace.
     */
    public static String deCabecera(String cabecera) {
        if (cabecera == null || cabecera.isBlank()) {
            return SIN_CABECERA;
        }
        String appId = cabecera.trim();
        return CONOCIDAS.contains(appId) ? appId : null;
    }
}
