package com.joaquim.habitosapp.controller;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.dto.MascotaDTO;
import com.joaquim.habitosapp.service.MascotaService;
import com.joaquim.habitosapp.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> obtener(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        MascotaDTO mascota = mascotaService.obtenerDTO(usuarioId);
        return ResponseEntity.ok(mascota);
    }

    @PutMapping("/{usuarioId}/nombre")
    public ResponseEntity<?> ponerNombre(@PathVariable int usuarioId,
                                         @RequestBody Map<String, String> body) {
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
}