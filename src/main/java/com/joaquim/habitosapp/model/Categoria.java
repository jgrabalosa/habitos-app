package com.joaquim.habitosapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categoria")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categoria_id")
    private int categoriaId;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "icono", length = 50)
    private String icono;

    @Column(name = "es_global", nullable = false)
    private boolean esGlobal;

    @Column(name = "orden")
    private int orden;

    @ManyToOne
    @JoinColumn(name = "creador_id", foreignKey = @ForeignKey(name = "FK_categoria_usuario"))
    private Usuario creador;

    // Constructor vacío — obligatorio para JPA
    public Categoria() {}

    // Constructor con parámetros
    public Categoria(String nombre, String descripcion, String color, String icono, boolean esGlobal, int orden, Usuario creador) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.color = color;
        this.icono = icono;
        this.esGlobal = esGlobal;
        this.orden = orden;
        this.creador = creador;
    }

    public int getCategoriaId() { return categoriaId; }
    public void setCategoriaId(int categoriaId) { this.categoriaId = categoriaId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public boolean isEsGlobal() { return esGlobal; }
    public void setEsGlobal(boolean esGlobal) { this.esGlobal = esGlobal; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public Usuario getCreador() { return creador; }
    public void setCreador(Usuario creador) { this.creador = creador; }

    @Override
    public String toString() {
        return "Categoria{categoriaId=" + categoriaId + ", nombre='" + nombre + "', esGlobal=" + esGlobal + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Categoria categoria = (Categoria) o;
        return categoriaId == categoria.categoriaId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(categoriaId);
    }
}