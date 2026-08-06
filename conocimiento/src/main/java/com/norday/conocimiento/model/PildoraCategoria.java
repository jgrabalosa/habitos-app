package com.norday.conocimiento.model;

import jakarta.persistence.*;

/**
 * Relación píldora ↔ categoría. Una píldora puede colgar de varias categorías;
 * una de ellas va marcada como principal.
 */
@Entity
@Table(name = "pildora_categoria")
public class PildoraCategoria {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "pildora_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pildora_categoria_pildora"))
    private Pildora pildora;

    @ManyToOne
    @JoinColumn(name = "categoria_id", nullable = false, foreignKey = @ForeignKey(name = "FK_pildora_categoria_categoria"))
    private Categoria categoria;

    @Column(name = "es_principal", nullable = false)
    private boolean esPrincipal = false;

    // Constructor vacío — obligatorio para JPA
    public PildoraCategoria() {}

    // Constructor con parámetros
    public PildoraCategoria(Pildora pildora, Categoria categoria, boolean esPrincipal) {
        this.pildora = pildora;
        this.categoria = categoria;
        this.esPrincipal = esPrincipal;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Pildora getPildora() { return pildora; }
    public void setPildora(Pildora pildora) { this.pildora = pildora; }

    public Categoria getCategoria() { return categoria; }
    public void setCategoria(Categoria categoria) { this.categoria = categoria; }

    public boolean isEsPrincipal() { return esPrincipal; }
    public void setEsPrincipal(boolean esPrincipal) { this.esPrincipal = esPrincipal; }

    @Override
    public String toString() {
        return "PildoraCategoria{id=" + id + ", esPrincipal=" + esPrincipal + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PildoraCategoria that = (PildoraCategoria) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
