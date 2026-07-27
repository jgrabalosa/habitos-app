package com.norday.core.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_id")
    private int usuarioId;

    @NotBlank(message = "El nombre es obligatorio")
    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @NotBlank(message = "El username es obligatorio")
    @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
    @Column(name = "username", nullable = false, unique = true, length = 50)
    private String username;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene un formato válido")
    @Column(name = "email", nullable = false, unique = true, length = 150)
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 6, message = "La contraseña debe tener al menos 6 caracteres")
    @Column(name = "contrasena", nullable = false, length = 255)
    private String contrasena;

    @Column(name = "proveedor_auth", nullable = false, length = 20)
    private String proveedorAuth = "LOCAL";

    @Column(name = "fcm_token", length = 255)
    private String fcmToken;

    @Column(name = "fecha_registro", nullable = false)
    private LocalDateTime fechaRegistro;

    // Preferencias. Idioma y zona son independientes a propósito: un brasileño
    // y un portugués hablan lo mismo y están a cuatro horas.
    // columnDefinition con DEFAULT: así ddl-auto=update puede añadir la
    // columna NOT NULL aunque la tabla ya tuviera filas.
    @Column(name = "idioma", nullable = false, length = 10,
            columnDefinition = "varchar(10) default 'es'")
    private String idioma = IDIOMA_POR_DEFECTO;

    @Column(name = "zona_horaria", nullable = false, length = 64,
            columnDefinition = "varchar(64) default 'Europe/Madrid'")
    private String zonaHoraria = ZONA_POR_DEFECTO;

    public static final String IDIOMA_POR_DEFECTO = "es";
    public static final String ZONA_POR_DEFECTO = "Europe/Madrid";

    // Constructor vacío — obligatorio para JPA
    public Usuario() {}

    // Constructor con parámetros
    public Usuario(String nombre, String username, String email, String contrasena) {
        this.nombre = nombre;
        this.username = username;
        this.email = email;
        this.contrasena = contrasena;
        this.fechaRegistro = LocalDateTime.now();
    }

    public int getUsuarioId() { return usuarioId; }
    public void setUsuarioId(int usuarioId) { this.usuarioId = usuarioId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getContrasena() { return contrasena; }
    public void setContrasena(String contrasena) { this.contrasena = contrasena; }

    public String getProveedorAuth() { return proveedorAuth; }
    public void setProveedorAuth(String proveedorAuth) { this.proveedorAuth = proveedorAuth; }

    public String getFcmToken() { return fcmToken; }
    public void setFcmToken(String fcmToken) { this.fcmToken = fcmToken; }

    public LocalDateTime getFechaRegistro() { return fechaRegistro; }
    public void setFechaRegistro(LocalDateTime fechaRegistro) { this.fechaRegistro = fechaRegistro; }

    public String getIdioma() { return idioma; }
    public void setIdioma(String idioma) { this.idioma = idioma; }

    public String getZonaHoraria() { return zonaHoraria; }
    public void setZonaHoraria(String zonaHoraria) { this.zonaHoraria = zonaHoraria; }

    @Override
    public String toString() {
        return "Usuario{usuarioId=" + usuarioId + ", username='" + username + "', email='" + email + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Usuario usuario = (Usuario) o;
        return usuarioId == usuario.usuarioId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(usuarioId);
    }
}