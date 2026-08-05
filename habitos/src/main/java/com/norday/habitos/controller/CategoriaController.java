package com.norday.habitos.controller;

import com.norday.core.model.Usuario;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController {

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/globales")
    public ResponseEntity<List<Categoria>> obtenerGlobales() {
        return ResponseEntity.ok(categoriaService.obtenerGlobales());
    }

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obtenerPorUsuario(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(categoriaService.obtenerTodas(usuario));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Categoria categoria) {
        try {
            categoriaService.crearCategoria(categoria);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Categoría creada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id,
                                        @RequestBody Categoria categoria) {
        try {
            categoria.setCategoriaId(id);
            categoriaService.actualizar(categoria);
            return ResponseEntity.ok("Categoría actualizada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id) {
        try {
            categoriaService.eliminar(id);
            return ResponseEntity.ok("Categoría eliminada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}