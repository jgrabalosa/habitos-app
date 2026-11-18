package com.norday.gamificacion.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;

import java.time.LocalDate;

/**
 * Una oferta tal como la usa la tienda y la ve el cliente en el catálogo:
 * desde, hasta (días, los dos incluidos) y precio. El producto solo sirve
 * para repartirlas y no viaja.
 */
public record OfertaDTO(@JsonIgnore int productoId, LocalDate desde, LocalDate hasta, int precio) {

    /** Si [dia] cae dentro, con los dos extremos incluidos. */
    public boolean incluye(LocalDate dia) {
        return !dia.isBefore(desde) && !dia.isAfter(hasta);
    }
}
