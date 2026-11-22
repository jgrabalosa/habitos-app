package com.norday.habitos.repository;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Categoria;
import java.util.List;

public interface ICategoriaDAO {

    void save(Categoria categoria);
    Categoria findById(int id);
    Categoria findByCodigo(String codigo);
    List<Categoria> findByCreador(Usuario creador);
    List<Categoria> findAll(Usuario creador);
    /** Cuántas categorías propias (no globales) ha creado el usuario. */
    long contarPropias(Usuario creador);
    /**
     * ¿Hay una categoría global o del usuario con ese nombre, sin distinguir
     * mayúsculas ni espacios a los lados? {@code excluirId} es la que se está
     * editando (no cuenta contra sí misma); 0 si se está creando.
     */
    boolean existeNombre(Usuario creador, String nombre, int excluirId);
    void update(Categoria categoria);
    void delete(int id);
}