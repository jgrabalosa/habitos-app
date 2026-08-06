package com.norday.conocimiento.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Estado de cada píldora para cada usuario. Una fila por par usuario-píldora:
 * `numDescartes` cuenta cuántas veces la ha apartado, así que sobrevive al
 * descarte en vez de crearse una fila nueva cada vez.
 */
@Entity
@Table(name = "usuario_pildora")
public class UsuarioPildora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "FK_usuario_pildora_usuario"))
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "pildora_id", nullable = false, foreignKey = @ForeignKey(name = "FK_usuario_pildora_pildora"))
    private Pildora pildora;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoPildora estado;

    @Column(name = "num_descartes", nullable = false)
    private int numDescartes = 0;

    @Column(name = "fecha_ultima_interaccion", nullable = false)
    private LocalDateTime fechaUltimaInteraccion;

    // Constructor vacío — obligatorio para JPA
    public UsuarioPildora() {}

    // Constructor con parámetros
    public UsuarioPildora(Usuario usuario, Pildora pildora, EstadoPildora estado, int numDescartes, LocalDateTime fechaUltimaInteraccion) {
        this.usuario = usuario;
        this.pildora = pildora;
        this.estado = estado;
        this.numDescartes = numDescartes;
        this.fechaUltimaInteraccion = fechaUltimaInteraccion;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Pildora getPildora() { return pildora; }
    public void setPildora(Pildora pildora) { this.pildora = pildora; }

    public EstadoPildora getEstado() { return estado; }
    public void setEstado(EstadoPildora estado) { this.estado = estado; }

    public int getNumDescartes() { return numDescartes; }
    public void setNumDescartes(int numDescartes) { this.numDescartes = numDescartes; }

    public LocalDateTime getFechaUltimaInteraccion() { return fechaUltimaInteraccion; }
    public void setFechaUltimaInteraccion(LocalDateTime fechaUltimaInteraccion) { this.fechaUltimaInteraccion = fechaUltimaInteraccion; }

    @Override
    public String toString() {
        return "UsuarioPildora{id=" + id + ", estado=" + estado + ", numDescartes=" + numDescartes + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioPildora that = (UsuarioPildora) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
