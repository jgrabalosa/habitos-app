package com.joaquim.habitosapp.controller;

import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.security.JwtUtil;
import com.joaquim.habitosapp.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtUtil jwtUtil;

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody Usuario usuario,
                                       BindingResult result) {
        if (result.hasErrors()) {
            String errores = result.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(java.util.stream.Collectors.joining(", "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
        }
        try {
            usuarioService.registrar(usuario);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Usuario registrado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario usuario) {
        try {
            Usuario encontrado = usuarioService.login(
                    usuario.getEmail(), usuario.getContrasena());
            String token = jwtUtil.generateToken(encontrado.getEmail());
            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "usuarioId", encontrado.getUsuarioId(),
                    "nombre", encontrado.getNombre(),
                    "username", encontrado.getUsername(),
                    "email", encontrado.getEmail()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        Usuario usuario = usuarioService.buscarPorId(id);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(Map.of(
                "usuarioId", usuario.getUsuarioId(),
                "nombre", usuario.getNombre(),
                "username", usuario.getUsername(),
                "email", usuario.getEmail()
        ));
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id,
                                        @RequestBody Usuario usuario) {
        try {
            usuario.setUsuarioId(id);
            usuarioService.actualizarPerfil(usuario);
            return ResponseEntity.ok("Usuario actualizado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id) {
        try {
            usuarioService.eliminarCuenta(id);
            return ResponseEntity.ok("Usuario eliminado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }
}