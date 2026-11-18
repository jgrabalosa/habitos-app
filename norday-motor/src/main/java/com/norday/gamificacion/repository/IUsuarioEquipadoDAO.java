package com.norday.gamificacion.repository;

/**
 * La tabla usuario_equipado: lo puesto, una fila por usuario y sitio. Para
 * leerlo, IUsuarioProductoDAO.findEquipadoPorCategoria.
 */
public interface IUsuarioEquipadoDAO {

    /** Pone [usuarioProductoId] en ese sitio: actualiza la fila o la crea. */
    void poner(int usuarioId, String categoria, int usuarioProductoId);

    /** Deja el sitio vacío, solo si lo puesto es [usuarioProductoId]. */
    void quitar(int usuarioId, String categoria, int usuarioProductoId);

    void deleteByUsuario(int usuarioId);
}
