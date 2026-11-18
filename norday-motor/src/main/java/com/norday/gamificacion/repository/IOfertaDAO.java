package com.norday.gamificacion.repository;

import com.norday.gamificacion.model.Oferta;
import com.norday.gamificacion.model.dto.OfertaDTO;

import java.util.Collection;
import java.util.List;

public interface IOfertaDAO {
    void save(Oferta oferta);

    /** Las ofertas de un producto, ordenadas por desde. */
    List<OfertaDTO> deProducto(int productoId);

    /** Las ofertas de todos esos productos en una sola consulta, ordenadas por desde. */
    List<OfertaDTO> deProductos(Collection<Integer> productoIds);
}
