package com.norday.conocimiento.model;

import jakarta.persistence.*;

/**
 * Una de las 3-5 tarjetas de una píldora, que se leen una tras otra (tabla
 * pildora_tarjeta, V38). El gancho no es una tarjeta: vive en
 * {@link Pildora#getPreviewCorto()}.
 *
 * Única por (píldora, orden). Al rehacer las tarjetas de una píldora hay que
 * borrar las viejas y hacer flush antes de insertar las nuevas: Hibernate
 * inserta antes de borrar y la restricción saltaría.
 */
@Entity
@Table(name = "pildora_tarjeta",
        uniqueConstraints = @UniqueConstraint(name = "UK_pildora_tarjeta_orden",
                columnNames = {"pildora_id", "orden"}))
public class PildoraTarjeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pildora_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_pildora_tarjeta_pildora"))
    private Pildora pildora;

    /** Posición de la tarjeta, desde 1. */
    @Column(name = "orden", nullable = false)
    private int orden;

    /** Markdown corto, sin tope útil de longitud. */
    @Column(name = "texto", nullable = false, columnDefinition = "TEXT")
    private String texto;

    // Constructor vacío — obligatorio para JPA
    public PildoraTarjeta() {}

    public PildoraTarjeta(Pildora pildora, int orden, String texto) {
        this.pildora = pildora;
        this.orden = orden;
        this.texto = texto;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Pildora getPildora() { return pildora; }
    public void setPildora(Pildora pildora) { this.pildora = pildora; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }

    public String getTexto() { return texto; }
    public void setTexto(String texto) { this.texto = texto; }

    @Override
    public String toString() {
        return "PildoraTarjeta{id=" + id + ", orden=" + orden + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PildoraTarjeta that = (PildoraTarjeta) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
