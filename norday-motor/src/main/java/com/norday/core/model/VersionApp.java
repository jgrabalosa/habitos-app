package com.norday.core.model;

import jakarta.persistence.*;

/**
 * Versión mínima (versionCode de Android) que cada app del ecosistema
 * necesita para funcionar. Por debajo, la app se bloquea y pide actualizar.
 * Se cambia a mano con un UPDATE en la VPS, sin compilar ni reiniciar.
 * Orden de siempre: primero la versión nueva publicada y visible en Play,
 * después subir la mínima.
 */
@Entity
@Table(name = "version_app")
public class VersionApp {

    @Id
    @Column(name = "app_id", length = 30)
    private String appId;

    @Column(name = "version_minima", nullable = false)
    private int versionMinima;

    // Constructor vacío — obligatorio para JPA
    public VersionApp() {}

    public VersionApp(String appId, int versionMinima) {
        this.appId = appId;
        this.versionMinima = versionMinima;
    }

    public String getAppId() { return appId; }
    public void setAppId(String appId) { this.appId = appId; }

    public int getVersionMinima() { return versionMinima; }
    public void setVersionMinima(int versionMinima) { this.versionMinima = versionMinima; }
}
