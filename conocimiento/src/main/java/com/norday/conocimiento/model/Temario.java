package com.norday.conocimiento.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

/**
 * Un recorrido ordenado de píldoras dentro de una rama (tabla temario, V37).
 *
 * Viene del repo de contenido y el importador lo actualiza por
 * {@code codigo}, nunca por id. No se borra: lo que sale del repo pasa a
 * {@code activo = false}.
 *
 * Los defaults son los de la migración, para que la tabla que genera
 * Hibernate en los tests sea la misma.
 */
@Entity
@Table(name = "temario",
        uniqueConstraints = @UniqueConstraint(name = "UK_temario_codigo", columnNames = {"codigo"}),
        indexes = @Index(name = "idx_temario_categoria", columnList = "categoria_id"))
public class Temario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "temario_id")
    private int temarioId;

    @Column(name = "codigo", nullable = false, length = 100)
    private String codigo;

    /** La rama a la que pertenece. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "categoria_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_temario_categoria"))
    private Categoria categoria;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Column(name = "orden", nullable = false)
    private int orden;

    @ColumnDefault("true")
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    // Constructor vacío — obligatorio para JPA
    public Temario() {}

    public Temario(String codigo, Categoria categoria, String titulo, String descripcion, int orden) {
        this.codigo = codigo;
        this.categoria = categoria;
        this.titulo = titulo;
        this.descripcion = descripcion;
        this.orden = orden;
    }

    public int getTemarioId() { return temarioId; }
    public void setTemarioId(int temarioId) { this.temarioId = temarioId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return "Temario{temarioId=" + temarioId + ", codigo='" + codigo + "', activo=" + activo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Temario temario = (Temario) o;
        return temarioId == temario.temarioId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(temarioId);
    }
}
