package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.repository.ICategoriaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoriaService {

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    public void crearCategoria(Categoria categoria) {
        // El código pertenece al catálogo global: lo pone la siembra, nunca el
        // cliente. Una categoría del usuario no se traduce, se muestra tal cual.
        categoria.setCodigo(null);
        categoria.setEsGlobal(false);
        categoriaDAO.save(categoria);

        if (categoria.getCreador() != null) {
            List<Categoria> categoriasDelUsuario = categoriaDAO.findByCreador(categoria.getCreador());
            logrosHabitosService.evaluarTrasCrearCategoria(categoria.getCreador(), categoriasDelUsuario);
        }
    }

    public Categoria buscarPorId(int id) {
        return categoriaDAO.findById(id);
    }

    public List<Categoria> obtenerGlobales() {
        return categoriaDAO.findGlobales();
    }

    public List<Categoria> obtenerPorUsuario(Usuario usuario) {
        return categoriaDAO.findByCreador(usuario);
    }

    public List<Categoria> obtenerTodas(Usuario usuario) {
        return categoriaDAO.findAll(usuario);
    }

    public void actualizar(Categoria categoria) {
        categoriaDAO.update(categoria);
    }

    public void eliminar(int id) {
        categoriaDAO.delete(id);
    }
}