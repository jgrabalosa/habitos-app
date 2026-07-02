package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "usuario_moneda")
public class UsuarioMoneda {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "movimiento_id")
    private int movimientoId;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuariomoneda_usuario"))
    private Usuario usuario;

    @Column(name = "cantidad", nullable = false)
    private int cantidad;

    @Column(name = "origen", nullable = false, length = 50)
    private String origen;

    @Column(name = "referencia_id")
    private Integer referenciaId;

    @Column(name = "descripcion", length = 255)
    private String descripcion;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    // Constructor vacío — obligatorio para JPA
    public UsuarioMoneda() {}

    // Constructor con parámetros
    public UsuarioMoneda(Usuario usuario, int cantidad, String origen,
                         Integer referenciaId, String descripcion) {
        this.usuario = usuario;
        this.cantidad = cantidad;
        this.origen = origen;
        this.referenciaId = referenciaId;
        this.descripcion = descripcion;
        this.fecha = LocalDateTime.now();
    }

    public int getMovimientoId() { return movimientoId; }
    public void setMovimientoId(int movimientoId) { this.movimientoId = movimientoId; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public String getOrigen() { return origen; }
    public void setOrigen(String origen) { this.origen = origen; }

    public Integer getReferenciaId() { return referenciaId; }
    public void setReferenciaId(Integer referenciaId) { this.referenciaId = referenciaId; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    @Override
    public String toString() {
        return "UsuarioMoneda{movimientoId=" + movimientoId + ", cantidad=" + cantidad + ", origen='" + origen + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioMoneda that = (UsuarioMoneda) o;
        return movimientoId == that.movimientoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(movimientoId);
    }
}