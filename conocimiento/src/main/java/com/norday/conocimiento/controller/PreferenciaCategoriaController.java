package com.norday.conocimiento.controller;

import com.norday.conocimiento.model.dto.PreferenciaCategoriaDTO;
import com.norday.conocimiento.service.PreferenciaCategoriaService;
import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/conocimiento/preferencias")
public class PreferenciaCategoriaController {

    @Autowired
    private PreferenciaCategoriaService preferenciaService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping
    public ResponseEntity<?> obtener(Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        return ResponseEntity.ok(preferenciaService.obtenerPreferencias(usuario));
    }

    /** El body es la lista completa de categorías, no un parche. */
    @PutMapping
    public ResponseEntity<?> actualizar(@RequestBody List<PreferenciaCategoriaDTO> preferencias,
                                        Authentication authentication) {
        try {
            Usuario usuario = usuarioAutenticado(authentication);
            preferenciaService.actualizarPreferencias(usuario, preferencias);
            return ResponseEntity.ok(preferenciaService.obtenerPreferencias(usuario));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    /**
     * Quién eres lo dice el token y solo el token: ningún endpoint de este
     * módulo acepta un usuarioId de fuera, así que no hay nada que comprobar.
     */
    private Usuario usuarioAutenticado(Authentication authentication) {
        UsuarioAutenticado autenticado = (UsuarioAutenticado) authentication.getPrincipal();
        return usuarioService.buscarPorId(autenticado.usuarioId());
    }
}
