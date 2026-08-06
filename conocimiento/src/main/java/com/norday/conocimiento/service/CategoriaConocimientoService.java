package com.norday.conocimiento.service;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.dto.CategoriaConocimientoDTO;
import com.norday.conocimiento.repository.ICategoriaConocimientoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;

@Service
public class CategoriaConocimientoService {

    @Autowired
    private ICategoriaConocimientoDAO categoriaDAO;

    public List<CategoriaConocimientoDTO> listarTodas() {
        List<CategoriaConocimientoDTO> dtos = new ArrayList<>();
        for (Categoria categoria : categoriaDAO.findAll()) {
            dtos.add(aDTO(categoria));
        }
        return dtos;
    }

    static CategoriaConocimientoDTO aDTO(Categoria categoria) {
        CategoriaConocimientoDTO dto = new CategoriaConocimientoDTO();
        dto.setCategoriaId(categoria.getCategoriaId());
        dto.setCodigo(categoria.getCodigo());
        dto.setNombre(categoria.getNombre());
        dto.setDescripcion(categoria.getDescripcion());
        dto.setColor(categoria.getColor());
        dto.setIcono(categoria.getIcono());
        dto.setOrden(categoria.getOrden());
        return dto;
    }
}
