package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_logro",
        uniqueConstraints = @UniqueConstraint(
                name = "UQ_usuario_logro",
                columnNames = {"usuario_id", "logro_id"}
        ))
public class UsuarioLogro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_logro_id")
    private int usuarioLogroId;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuariologro_usuario"))
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "logro_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuariologro_logro"))
    private Logro logro;

    @Column(name = "fecha_conseguido", nullable = false)
    private LocalDateTime fechaConseguido;

    // Constructor vacío — obligatorio para JPA
    public UsuarioLogro() {}

    // Constructor con parámetros
    public UsuarioLogro(Usuario usuario, Logro logro) {
        this.usuario = usuario;
        this.logro = logro;
        this.fechaConseguido = LocalDateTime.now();
    }

    public int getUsuarioLogroId() { return usuarioLogroId; }
    public void setUsuarioLogroId(int usuarioLogroId) { this.usuarioLogroId = usuarioLogroId; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Logro getLogro() { return logro; }
    public void setLogro(Logro logro) { this.logro = logro; }

    public LocalDateTime getFechaConseguido() { return fechaConseguido; }
    public void setFechaConseguido(LocalDateTime fechaConseguido) { this.fechaConseguido = fechaConseguido; }

    @Override
    public String toString() {
        return "UsuarioLogro{usuarioLogroId=" + usuarioLogroId + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioLogro that = (UsuarioLogro) o;
        return usuarioLogroId == that.usuarioLogroId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(usuarioLogroId);
    }
}