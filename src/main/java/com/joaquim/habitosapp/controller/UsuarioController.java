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
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;
import com.joaquim.habitosapp.service.RecuperacionService;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RecuperacionService recuperacionService;

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
                    "email", encontrado.getEmail(),
                    "proveedorAuth", encontrado.getProveedorAuth()
            ));
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(e.getMessage());
        }
    }

    @PostMapping("/login-google")
    public ResponseEntity<?> loginGoogle(@RequestBody Map<String, String> body) {
        try {
            String idTokenString = body.get("idToken");

            GoogleIdTokenVerifier verifier = new GoogleIdTokenVerifier.Builder(
                    new NetHttpTransport(), GsonFactory.getDefaultInstance())
                    .setAudience(Collections.singletonList(
                            "177339814167-fdtmn2i1s6aeg1agrqtikq066opib8ce.apps.googleusercontent.com"))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token de Google inválido");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String nombre = (String) payload.get("name");

            Usuario usuario = usuarioService.loginConGoogle(email, nombre);
            String token = jwtUtil.generateToken(usuario.getEmail());

            return ResponseEntity.ok(Map.of(
                    "token", token,
                    "usuarioId", usuario.getUsuarioId(),
                    "nombre", usuario.getNombre(),
                    "username", usuario.getUsername(),
                    "email", usuario.getEmail(),
                    "proveedorAuth", usuario.getProveedorAuth()
            ));
        } catch (Exception e) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Error al verificar token de Google");
        }
    }

    @PostMapping("/recuperar")
    public ResponseEntity<?> recuperarContrasena(@RequestBody Map<String, String> body) {
        try {
            recuperacionService.solicitarCodigo(body.get("email"));
        } catch (Exception e) {
            // No revelamos nada: la respuesta es la misma en todos los casos
            System.out.println("Error al enviar código de recuperación: " + e.getMessage());
        }
        return ResponseEntity.ok(
                "Si el email está registrado, recibirás un código de recuperación");
    }

    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecerContrasena(@RequestBody Map<String, String> body) {
        try {
            recuperacionService.restablecerContrasena(
                    body.get("email"),
                    body.get("codigo"),
                    body.get("contrasenaNueva")
            );
            return ResponseEntity.ok("Contraseña restablecida correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
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

    @PutMapping("/{id}/contrasena")
    public ResponseEntity<?> cambiarContrasena(@PathVariable int id,
                                               @RequestBody Map<String, String> body) {
        try {
            usuarioService.cambiarContrasena(id,
                    body.get("contrasenaActual"),
                    body.get("contrasenaNueva"));
            return ResponseEntity.ok("Contraseña actualizada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PutMapping("/{id}/fcm-token")
    public ResponseEntity<?> actualizarFcmToken(@PathVariable int id,
                                                @RequestBody Map<String, String> body) {
        try {
            String fcmToken = body.get("fcmToken");
            usuarioService.actualizarFcmToken(id, fcmToken);
            return ResponseEntity.ok("Token FCM actualizado correctamente");
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