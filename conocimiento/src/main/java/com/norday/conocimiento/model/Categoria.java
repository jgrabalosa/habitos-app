package com.norday.conocimiento.model;

import jakarta.persistence.*;

/**
 * Categoría de conocimiento. Tabla propia (`categoria_conocimiento`): la
 * `categoria` de hábitos ya existe y es otro concepto, sin relación con este.
 *
 * Más simple que la de hábitos: aquí no hay `creador` ni `esGlobal` porque
 * todas las categorías son curadas — el usuario no crea ninguna.
 *
 * El nombre de entidad va explícito porque Hibernate exige que sea único en
 * toda la aplicación y el simple de la clase choca con el de hábitos. Es el
 * nombre que hay que usar en JPQL, no `Categoria`.
 */
@Entity(name = "CategoriaConocimiento")
@Table(name = "categoria_conocimiento")
public class Categoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "categoria_id")
    private int categoriaId;

    /**
     * Código estable del catálogo, para que el cliente pueda traducir la
     * categoría sin depender del texto que manda el servidor.
     */
    @Column(name = "codigo", unique = true, length = 50)
    private String codigo;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 200)
    private String descripcion;

    @Column(name = "color", length = 50)
    private String color;

    @Column(name = "icono", length = 50)
    private String icono;

    @Column(name = "orden")
    private int orden;

    // Constructor vacío — obligatorio para JPA
    public Categoria() {}

    // Constructor con parámetros
    public Categoria(String codigo, String nombre, String descripcion, String color, String icono, int orden) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.color = color;
        this.icono = icono;
        this.orden = orden;
    }

    public int getCategoriaId() { return categoriaId; }
    public void setCategoriaId(int categoriaId) { this.categoriaId = categoriaId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    @Override
    public String toString() {
        return "Categoria{categoriaId=" + categoriaId + ", codigo='" + codigo + "', nombre='" + nombre + "'}";
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
