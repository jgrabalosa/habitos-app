package com.norday.gamificacion.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.Check;

import java.time.LocalDate;

/**
 * Un periodo en el que un producto se vende a un precio (tabla oferta, V28).
 * Un producto con ofertas solo se compra dentro de una vigente y a su precio;
 * sin ofertas, se compra como siempre. Precio 0 es un regalo. Las fechas son
 * días de la zona del usuario, los dos incluidos.
 *
 * Producto no tiene una relación JPA hacia sus ofertas a propósito: el
 * catálogo y el inventario devuelven la entidad tal cual y una relación
 * perezosa reventaría al pasar a JSON. Las ofertas del catálogo las pone
 * ProductoService en una sola consulta.
 */
@Entity
@Check(constraints = "precio >= 0 AND desde <= hasta")
@Table(name = "oferta", indexes = @Index(name = "idx_oferta_producto", columnList = "producto_id"))
public class Oferta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "oferta_id")
    private int ofertaId;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "producto_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_oferta_producto"))
    private Producto producto;

    @Column(name = "desde", nullable = false)
    private LocalDate desde;

    @Column(name = "hasta", nullable = false)
    private LocalDate hasta;

    @Column(name = "precio", nullable = false)
    private int precio;

    public Oferta() {}

    public Oferta(Producto producto, LocalDate desde, LocalDate hasta, int precio) {
        this.producto = producto;
        this.desde = desde;
        this.hasta = hasta;
        this.precio = precio;
    }

    public int getOfertaId() { return ofertaId; }
    public Producto getProducto() { return producto; }
    public LocalDate getDesde() { return desde; }
    public LocalDate getHasta() { return hasta; }
    public int getPrecio() { return precio; }
}
