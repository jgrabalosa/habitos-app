package com.norday.gamificacion.controller;

import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.dto.MascotaDTO;
import com.norday.gamificacion.service.MascotaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/mascota")
public class MascotaController {

    @Autowired
    private MascotaService mascotaService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/{usuarioId}")
    public ResponseEntity<?> obtener(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        MascotaDTO mascota = mascotaService.obtenerDTO(usuarioId);
        return ResponseEntity.ok(mascota);
    }

    @PutMapping("/{usuarioId}/nombre")
    public ResponseEntity<?> ponerNombre(@PathVariable int usuarioId,
                                         @RequestBody Map<String, String> body,
                                         Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        String nombre = body.get("nombre");
        if (nombre == null || nombre.isBlank()) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("El nombre no puede estar vacío");
        }
        mascotaService.ponerNombre(usuarioId, nombre);
        return ResponseEntity.ok("Nombre actualizado correctamente");
    }

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes operar sobre tu propia cuenta");
    }

    private boolean esElUsuarioAutenticado(int idUrl, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && autenticado.usuarioId() == idUrl;
    }
}
