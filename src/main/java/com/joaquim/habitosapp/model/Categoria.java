package com.joaquim.habitosapp.model;

import jakarta.persistence.*;

@Entity
@Table(name = "categoria")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int categoriaId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 50)
    private String color;

    @Column(length = 50)
    private String icono;

    @Column(nullable = false)
    private boolean esGlobal;

    @ManyToOne
    @JoinColumn(name = "creador_id")
    private Usuario creador;

    // Constructor vacío — obligatorio para JPA
    public Categoria() {}

    // Constructor con parámetros
    public Categoria(String nombre, String color, String icono, boolean esGlobal, Usuario creador) {
        this.nombre = nombre;
        this.color = color;
        this.icono = icono;
        this.esGlobal = esGlobal;
        this.creador = creador;
    }

    // Getters y Setters
    public int getCategoriaId() { return categoriaId; }
    public void setCategoriaId(int categoriaId) { this.categoriaId = categoriaId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public boolean isEsGlobal() { return esGlobal; }
    public void setEsGlobal(boolean esGlobal) { this.esGlobal = esGlobal; }

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