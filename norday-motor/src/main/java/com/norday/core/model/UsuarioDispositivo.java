package com.norday.core.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * El token de notificaciones de un usuario en una app del ecosistema.
 *
 * FCM genera un token por instancia de app («Cuando se inicia tu app por
 * primera vez, el SDK de FCM genera un token de registro para la instancia de
 * la app cliente», https://firebase.google.com/docs/cloud-messaging/manage-tokens),
 * así que dos apps de Norday en el mismo móvil tienen dos tokens distintos. Con
 * uno solo por usuario, la última app en arrancar pisaba a las demás.
 *
 * Una fila por (usuario, app): el último token que registró esa app. La misma
 * página recomienda guardar el token «junto con una marca de tiempo», que es
 * actualizado_en, en UTC como los demás sellos de auditoría.
 */
@Entity
@Table(name = "usuario_dispositivo",
        uniqueConstraints = @UniqueConstraint(name = "UK_usuario_dispositivo_usuario_app",
                columnNames = {"usuario_ref", "app_id"}))
public class UsuarioDispositivo {

    /** Tope generoso: FCM no publica una longitud máxima del token. */
    public static final int LONGITUD_MAXIMA_TOKEN = 4096;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_ref", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuario_dispositivo_usuario"))
    private Usuario usuario;

    @Column(name = "app_id", nullable = false, length = 30)
    private String appId;

    @Column(name = "fcm_token", nullable = false, length = LONGITUD_MAXIMA_TOKEN)
    private String fcmToken;

    @Column(name = "actualizado_en", nullable = false)
    private LocalDateTime actualizadoEn;

    // Constructor vacío — obligatorio para JPA
    public UsuarioDispositivo() {}

    public UsuarioDispositivo(Usuario usuario, String appId, String fcmToken, LocalDateTime actualizadoEn) {
        this.usuario = usuario;
        this.appId = appId;
        this.fcmToken = fcmToken;
        this.actualizadoEn = actualizadoEn;
    }

    public int getId() { return id; }

    public Usuario getUsuario() { return usuario; }

    public String getAppId() { return appId; }

    public String getFcmToken() { return fcmToken; }

    public LocalDateTime getActualizadoEn() { return actualizadoEn; }
}
