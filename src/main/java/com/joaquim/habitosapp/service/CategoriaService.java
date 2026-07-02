package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Categoria;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.ICategoriaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class CategoriaService {

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private MotorLogrosService motorLogrosService;

    public void crearCategoria(Categoria categoria) {
        categoriaDAO.save(categoria);

        if (categoria.getCreador() != null) {
            List<Categoria> categoriasDelUsuario = categoriaDAO.findByCreador(categoria.getCreador());
            motorLogrosService.evaluarTrasCrearCategoria(categoria.getCreador(), categoriasDelUsuario);
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