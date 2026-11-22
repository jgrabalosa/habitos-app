package com.norday.habitos.model.dto;

import com.norday.habitos.model.Categoria;

/**
 * Lo que sale de /api/categorias. Las mismas claves que salían con la entidad
 * menos {@code creador} (el Usuario entero): la app ya instalada sigue
 * funcionando sin cambios. Nunca se devuelve la entidad con sus relaciones.
 */
public record CategoriaDTO(int categoriaId, String codigo, String nombre, String descripcion,
                           String color, String icono, boolean esGlobal, int orden) {

    public static CategoriaDTO de(Categoria categoria) {
        return new CategoriaDTO(categoria.getCategoriaId(), categoria.getCodigo(), categoria.getNombre(),
                categoria.getDescripcion(), categoria.getColor(), categoria.getIcono(),
                categoria.isEsGlobal(), categoria.getOrden());
    }
}
