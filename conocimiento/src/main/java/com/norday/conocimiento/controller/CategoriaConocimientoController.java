package com.norday.conocimiento.controller;

import com.norday.conocimiento.model.dto.CategoriaConocimientoDTO;
import com.norday.conocimiento.service.CategoriaConocimientoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

@RestController
@RequestMapping("/api/conocimiento/categorias")
public class CategoriaConocimientoController {

    @Autowired
    private CategoriaConocimientoService categoriaService;

    @GetMapping
    public ResponseEntity<List<CategoriaConocimientoDTO>> listar() {
        return ResponseEntity.ok(categoriaService.listarTodas());
    }
}
