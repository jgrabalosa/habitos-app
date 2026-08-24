package com.norday.habitos.controller;

import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
    public ResponseEntity<?> obtenerPorUsuario(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(categoriaService.obtenerTodas(usuario));
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Categoria categoria, Authentication authentication) {
        // El creador nunca se toma del body: se fija desde el token, para que
        // nadie pueda crear una categoría a nombre de otro usuario.
        Usuario autenticado = usuarioAutenticadoComoUsuario(authentication);
        if (autenticado == null) {
            return prohibido();
        }
        categoria.setCreador(autenticado);
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
                                        @RequestBody Categoria categoria,
                                        Authentication authentication) {
        Categoria existente = categoriaService.buscarPorId(id);
        if (existente == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Categoría no encontrada");
        }
        if (!esElCreador(existente, authentication)) {
            return prohibido();
        }
        try {
            // categoriaId, codigo, esGlobal y creador se conservan de la
            // categoría ya existente, nunca del body: solo los campos de
            // abajo son editables por el usuario.
            existente.setNombre(categoria.getNombre());
            existente.setDescripcion(categoria.getDescripcion());
            existente.setColor(categoria.getColor());
            existente.setIcono(categoria.getIcono());
            existente.setOrden(categoria.getOrden());
            categoriaService.actualizar(existente);
            return ResponseEntity.ok("Categoría actualizada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication authentication) {
        Categoria existente = categoriaService.buscarPorId(id);
        if (existente == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Categoría no encontrada");
        }
        if (!esElCreador(existente, authentication)) {
            return prohibido();
        }
        try {
            categoriaService.eliminar(id);
            return ResponseEntity.ok("Categoría eliminada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    // No existe rol de administrador en el proyecto, así que ninguna
    // categoría global (creador null) es editable ni borrable por esta vía:
    // solo las categorías personales del propio usuario.

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes operar sobre tus propias categorías");
    }

    private boolean esElUsuarioAutenticado(int idUrl, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && autenticado.usuarioId() == idUrl;
    }

    /** ¿El creador de esta categoría es el usuario que trae el token? Las globales (creador null) nunca lo son. */
    private boolean esElCreador(Categoria categoria, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && categoria.getCreador() != null
                && categoria.getCreador().getUsuarioId() == autenticado.usuarioId();
    }

    private Usuario usuarioAutenticadoComoUsuario(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioAutenticado autenticado)) {
            return null;
        }
        return usuarioService.buscarPorId(autenticado.usuarioId());
    }
}
