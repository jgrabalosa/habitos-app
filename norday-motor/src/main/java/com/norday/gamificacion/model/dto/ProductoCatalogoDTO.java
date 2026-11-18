package com.norday.gamificacion.model.dto;

import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.norday.gamificacion.model.Producto;

import java.util.List;

/**
 * Un producto del catálogo con sus ofertas. En JSON es el producto tal cual
 * (los mismos campos que antes, por @JsonUnwrapped) más «ofertas»: las que
 * aún no han terminado, ordenadas por desde; vacía si no tiene. Las APK que
 * no conocen «ofertas» la ignoran.
 */
public class ProductoCatalogoDTO {

    private final Producto producto;
    private final List<OfertaDTO> ofertas;

    public ProductoCatalogoDTO(Producto producto, List<OfertaDTO> ofertas) {
        this.producto = producto;
        this.ofertas = ofertas;
    }

    @JsonUnwrapped
    public Producto getProducto() { return producto; }

    public List<OfertaDTO> getOfertas() { return ofertas; }
}
