package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_producto")
public class UsuarioProducto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "usuario_producto_id")
    private int usuarioProductoId;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuarioproducto_usuario"))
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "producto_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuarioproducto_producto"))
    private Producto producto;

    @Column(name = "fecha_adquirido", nullable = false)
    private LocalDateTime fechaAdquirido;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    @Column(name = "equipado", nullable = false)
    private boolean equipado = false;

    // Constructor vacío — obligatorio para JPA
    public UsuarioProducto() {}

    // Constructor con parámetros
    public UsuarioProducto(Usuario usuario, Producto producto, int cantidad) {
        this.usuario = usuario;
        this.producto = producto;
        this.cantidad = cantidad;
        this.fechaAdquirido = LocalDateTime.now();
        this.equipado = false;
    }

    public int getUsuarioProductoId() { return usuarioProductoId; }
    public void setUsuarioProductoId(int usuarioProductoId) { this.usuarioProductoId = usuarioProductoId; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public LocalDateTime getFechaAdquirido() { return fechaAdquirido; }
    public void setFechaAdquirido(LocalDateTime fechaAdquirido) { this.fechaAdquirido = fechaAdquirido; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public boolean isEquipado() { return equipado; }
    public void setEquipado(boolean equipado) { this.equipado = equipado; }

    @Override
    public String toString() {
        return "UsuarioProducto{usuarioProductoId=" + usuarioProductoId + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioProducto that = (UsuarioProducto) o;
        return usuarioProductoId == that.usuarioProductoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(usuarioProductoId);
    }
}