package com.norday.conocimiento.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Preferencia del usuario sobre una categoría.
 *
 * La regla de "mínimo una categoría en QUIERE por usuario" se valida en el
 * service, no aquí: no es una restricción que la BD pueda expresar por fila.
 */
@Entity
@Table(name = "usuario_categoria_preferencia")
public class UsuarioCategoriaPreferencia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "FK_usuario_categoria_pref_usuario"))
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "categoria_id", nullable = false, foreignKey = @ForeignKey(name = "FK_usuario_categoria_pref_categoria"))
    private Categoria categoria;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 30)
    private EstadoPreferenciaCategoria estado;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    // Constructor vacío — obligatorio para JPA
    public UsuarioCategoriaPreferencia() {}

    // Constructor con parámetros
    public UsuarioCategoriaPreferencia(Usuario usuario, Categoria categoria, EstadoPreferenciaCategoria estado, LocalDateTime fecha) {
        this.usuario = usuario;
        this.categoria = categoria;
        this.estado = estado;
        this.fecha = fecha;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public EstadoPreferenciaCategoria getEstado() { return estado; }
    public void setEstado(EstadoPreferenciaCategoria estado) { this.estado = estado; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    @Override
    public String toString() {
        return "UsuarioCategoriaPreferencia{id=" + id + ", estado=" + estado + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioCategoriaPreferencia that = (UsuarioCategoriaPreferencia) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
