package com.norday.habitos.controller;

import com.norday.core.model.Usuario;
import com.norday.core.security.ControladorAutorizado;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.dto.CategoriaDTO;
import com.norday.habitos.model.dto.CategoriaEntradaDTO;
import com.norday.habitos.service.CategoriaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/categorias")
public class CategoriaController extends ControladorAutorizado {

    @Autowired
    private CategoriaService categoriaService;

    @Autowired
    private UsuarioService usuarioService;

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
        return ResponseEntity.ok(categoriaService.obtenerTodas(usuario).stream()
                .map(CategoriaDTO::de)
                .toList());
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody CategoriaEntradaDTO entrada, Authentication authentication) {
        // El creador nunca se toma del body: se fija desde el token, para que
        // nadie pueda crear una categoría a nombre de otro usuario.
        Usuario autenticado = usuarioAutenticadoComoUsuario(authentication);
        if (autenticado == null) {
            return prohibido();
        }
        Categoria creada = categoriaService.crear(autenticado, entrada);
        return ResponseEntity.status(HttpStatus.CREATED).body(CategoriaDTO.de(creada));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id,
                                        @RequestBody CategoriaEntradaDTO entrada,
                                        Authentication authentication) {
        Categoria existente = categoriaService.buscarPorId(id);
        if (existente == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Categoría no encontrada");
        }
        if (!esElCreador(existente, authentication)) {
            return prohibido();
        }
        // Solo nombre e icono son editables; lo demás se conserva.
        return ResponseEntity.ok(CategoriaDTO.de(categoriaService.actualizar(id, entrada)));
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
        categoriaService.eliminar(id);
        return ResponseEntity.ok("Categoría eliminada correctamente");
    }

    // No existe rol de administrador en el proyecto, así que ninguna
    // categoría global (creador null) es editable ni borrable por esta vía:
    // solo las categorías personales del propio usuario.

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

    @Override
    protected String mensajeProhibido() {
        return "Solo puedes operar sobre tus propias categorías";
    }
}
