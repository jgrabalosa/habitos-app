package com.norday.gamificacion.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "producto")
public class Producto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "producto_id")
    private int productoId;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "codigo", nullable = false, length = 50, unique = true)
    private String codigo;

    @Column(name = "categoria", nullable = false, length = 50)
    private String categoria;

    @Column(name = "tipo", nullable = false, length = 20)
    private String tipo;

    @Column(name = "precio", nullable = false)
    private int precio;

    @Column(name = "icono", length = 100)
    private String icono;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    /**
     * De qué app del ecosistema es este producto. NULL significa compartido:
     * lo ven todas. Nullable a propósito — el catálogo que ya existe es de
     * todos y no hay que migrar ni una fila.
     */
    @Column(name = "origen_app", length = 30)
    private String origenApp;

    // Constructor vacío — obligatorio para JPA
    public Producto() {}

    // Constructor con parámetros — producto compartido por todo el ecosistema
    public Producto(String codigo, String nombre, String descripcion, String categoria,
                    String tipo, int precio, String icono) {
        this(codigo, nombre, descripcion, categoria, tipo, precio, icono, null);
    }

    // Igual, pero exclusivo de una app
    public Producto(String codigo, String nombre, String descripcion, String categoria,
                    String tipo, int precio, String icono, String origenApp) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.tipo = tipo;
        this.precio = precio;
        this.icono = icono;
        this.origenApp = origenApp;
        this.activo = true;
    }

    public int getProductoId() { return productoId; }
    public void setProductoId(int productoId) { this.productoId = productoId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getTipo() { return tipo; }
    public void setTipo(String tipo) { this.tipo = tipo; }

    public int getPrecio() { return precio; }
    public void setPrecio(int precio) { this.precio = precio; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public String getOrigenApp() { return origenApp; }
    public void setOrigenApp(String origenApp) { this.origenApp = origenApp; }

    @Override
    public String toString() {
        return "Producto{productoId=" + productoId + ", codigo='" + codigo + "', nombre='" + nombre + "', tipo='" + tipo + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Producto producto = (Producto) o;
        return productoId == producto.productoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(productoId);
    }
}