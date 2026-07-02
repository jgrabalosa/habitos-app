package com.joaquim.habitosapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "perfil_gamificacion")
public class PerfilGamificacion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "perfil_gamificacion_id")
    private int perfilGamificacionId;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "FK_perfilgamificacion_usuario"))
    private Usuario usuario;

    // Constructor vacío — obligatorio para JPA
    public PerfilGamificacion() {}

    // Constructor con parámetros
    public PerfilGamificacion(Usuario usuario) {
        this.usuario = usuario;
    }

    public int getPerfilGamificacionId() { return perfilGamificacionId; }
    public void setPerfilGamificacionId(int perfilGamificacionId) { this.perfilGamificacionId = perfilGamificacionId; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    @Override
    public String toString() {
        return "PerfilGamificacion{perfilGamificacionId=" + perfilGamificacionId + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PerfilGamificacion that = (PerfilGamificacion) o;
        return perfilGamificacionId == that.perfilGamificacionId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(perfilGamificacionId);
    }
}