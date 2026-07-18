package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "codigo_recuperacion")
public class CodigoRecuperacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "codigo_id")
    private int codigoId;

    @Column(name = "email", nullable = false, length = 150)
    private String email;

    @Column(name = "codigo", nullable = false, length = 6)
    private String codigo;

    @Column(name = "fecha_expiracion", nullable = false)
    private LocalDateTime fechaExpiracion;

    @Column(name = "usado", nullable = false)
    private boolean usado = false;

    // Constructor vacío — obligatorio para JPA
    public CodigoRecuperacion() {}

    public CodigoRecuperacion(String email, String codigo, LocalDateTime fechaExpiracion) {
        this.email = email;
        this.codigo = codigo;
        this.fechaExpiracion = fechaExpiracion;
    }

    public int getCodigoId() { return codigoId; }
    public void setCodigoId(int codigoId) { this.codigoId = codigoId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public LocalDateTime getFechaExpiracion() { return fechaExpiracion; }
    public void setFechaExpiracion(LocalDateTime fechaExpiracion) { this.fechaExpiracion = fechaExpiracion; }

    public boolean isUsado() { return usado; }
    public void setUsado(boolean usado) { this.usado = usado; }
}