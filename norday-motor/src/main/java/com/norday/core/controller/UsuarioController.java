package com.norday.core.controller;

import com.norday.core.model.Usuario;
import com.norday.core.model.dto.ResultadoLoginGoogle;
import com.norday.core.security.JwtUtil;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.PreferenciasService;
import com.norday.core.service.RecuperacionService;
import com.norday.core.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.validation.BindingResult;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdToken;
import com.google.api.client.googleapis.auth.oauth2.GoogleIdTokenVerifier;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import java.util.Collections;

@RestController
@RequestMapping("/api/usuarios")
public class UsuarioController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(UsuarioController.class);

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private JwtUtil jwtUtil;

    @Autowired
    private RecuperacionService recuperacionService;

    @Autowired
    private PreferenciasService preferenciasService;

    @PostMapping("/registro")
    public ResponseEntity<?> registrar(@Valid @RequestBody Usuario usuario,
                                       BindingResult result) {
        if (result.hasErrors()) {
            String errores = result.getFieldErrors().stream()
                    .map(e -> e.getField() + ": " + e.getDefaultMessage())
                    .collect(java.util.stream.Collectors.joining(", "));
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(errores);
        }
        usuarioService.registrar(usuario);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body("Usuario registrado correctamente");
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Usuario usuario) {
        // Este catch se conserva a propósito: el login siempre debe devolver
        // 401 en cualquier fallo de credenciales, no el 400 por defecto del
        // manejador global.
        try {
            Usuario encontrado = usuarioService.login(
                    usuario.getEmail(), usuario.getContrasena());
            String token = jwtUtil.generateToken(
                    encontrado.getUsuarioId(), encontrado.getEmail());
            return ResponseEntity.ok(payloadSesion(encontrado, token, null));
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
                            "1086143132391-vprrrjr7s3u12q544flm2tclllj61ami.apps.googleusercontent.com"))
                    .build();

            GoogleIdToken idToken = verifier.verify(idTokenString);
            if (idToken == null) {
                return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Token de Google inválido");
            }

            GoogleIdToken.Payload payload = idToken.getPayload();
            String email = payload.getEmail();
            String nombre = (String) payload.get("name");

            ResultadoLoginGoogle resultado = usuarioService.loginConGoogle(email, nombre);
            Usuario usuario = resultado.usuario();
            String token = jwtUtil.generateToken(
                    usuario.getUsuarioId(), usuario.getEmail());

            return ResponseEntity.ok(payloadSesion(usuario, token, resultado.esNuevo()));
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
            log.warn("Error al enviar código de recuperación: {}", e.getMessage());
        }
        return ResponseEntity.ok(
                "Si el email está registrado, recibirás un código de recuperación");
    }

    @PostMapping("/restablecer")
    public ResponseEntity<?> restablecerContrasena(@RequestBody Map<String, String> body) {
        recuperacionService.restablecerContrasena(
                body.get("email"),
                body.get("codigo"),
                body.get("contrasenaNueva")
        );
        return ResponseEntity.ok("Contraseña restablecida correctamente");
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id,
                                         Authentication authentication) {
        if (!esElUsuarioAutenticado(id, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(id);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(Map.of(
                "usuarioId", usuario.getUsuarioId(),
                "nombre", usuario.getNombre(),
                "username", usuario.getUsername(),
                "email", usuario.getEmail(),
                "idioma", usuario.getIdioma(),
                "zonaHoraria", usuario.getZonaHoraria()
        ));
    }

    // ── Preferencias: idioma y zona horaria ────────────────
    @GetMapping("/{id}/preferencias")
    public ResponseEntity<?> obtenerPreferencias(@PathVariable int id,
                                                 Authentication authentication) {
        if (!esElUsuarioAutenticado(id, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(id);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        return ResponseEntity.ok(Map.of(
                "idioma", usuario.getIdioma(),
                "zonaHoraria", usuario.getZonaHoraria()
        ));
    }

    /** Cada campo es opcional: se envía solo el que se quiere cambiar. */
    @PutMapping("/{id}/preferencias")
    public ResponseEntity<?> actualizarPreferencias(@PathVariable int id,
                                                    @RequestBody Map<String, String> body,
                                                    Authentication authentication) {
        if (!esElUsuarioAutenticado(id, authentication)) {
            return prohibido();
        }
        Usuario usuario = preferenciasService.actualizar(
                id, body.get("idioma"), body.get("zonaHoraria"));
        return ResponseEntity.ok(Map.of(
                "idioma", usuario.getIdioma(),
                "zonaHoraria", usuario.getZonaHoraria()
        ));
    }

    /**
     * Payload común de sesión (login normal y Google). esNuevo solo viaja
     * cuando el llamador lo aporta: el login normal no lo incluye.
     */
    private Map<String, Object> payloadSesion(Usuario usuario, String token, Boolean esNuevo) {
        Map<String, Object> payload = new java.util.LinkedHashMap<>();
        payload.put("token", token);
        payload.put("usuarioId", usuario.getUsuarioId());
        payload.put("nombre", usuario.getNombre());
        payload.put("username", usuario.getUsername());
        payload.put("email", usuario.getEmail());
        payload.put("proveedorAuth", usuario.getProveedorAuth());
        payload.put("idioma", usuario.getIdioma());
        payload.put("zonaHoraria", usuario.getZonaHoraria());
        if (esNuevo != null) {
            payload.put("esNuevo", esNuevo);
        }
        return payload;
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id,
                                        @RequestBody Usuario usuario,
                                        Authentication authentication) {
        if (!esElUsuarioAutenticado(id, authentication)) {
            return prohibido();
        }
        usuario.setUsuarioId(id);
        usuarioService.actualizarPerfil(usuario);
        return ResponseEntity.ok("Usuario actualizado correctamente");
    }

    @PutMapping("/{id}/contrasena")
    public ResponseEntity<?> cambiarContrasena(@PathVariable int id,
                                               @RequestBody Map<String, String> body,
                                               Authentication authentication) {
        if (!esElUsuarioAutenticado(id, authentication)) {
            return prohibido();
        }
        usuarioService.cambiarContrasena(id,
                body.get("contrasenaActual"),
                body.get("contrasenaNueva"));
        return ResponseEntity.ok("Contraseña actualizada correctamente");
    }

    @PutMapping("/{id}/fcm-token")
    public ResponseEntity<?> actualizarFcmToken(@PathVariable int id,
                                                @RequestBody Map<String, String> body,
                                                Authentication authentication) {
        if (!esElUsuarioAutenticado(id, authentication)) {
            return prohibido();
        }
        String fcmToken = body.get("fcmToken");
        usuarioService.actualizarFcmToken(id, fcmToken);
        return ResponseEntity.ok("Token FCM actualizado correctamente");
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id,
                                      Authentication authentication) {
        // El {id} de la URL lo elige el cliente: no prueba nada. Quien eres lo
        // dice el token y solo el token. Sin esta comprobación, cualquier
        // usuario con sesión válida podía borrar otra cuenta cambiando el número.
        if (!esElUsuarioAutenticado(id, authentication)) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("No puedes eliminar la cuenta de otro usuario");
        }
        usuarioService.eliminarCuenta(id);
        return ResponseEntity.ok("Usuario eliminado correctamente");
    }

    // ── Autorización ───────────────────────────────────────
    // Todos los endpoints con {id} son de uso exclusivo del propio usuario:
    // no existe rol de administrador en el proyecto (el token no lleva
    // authorities y Usuario no tiene campo de rol), así que nadie tiene
    // motivo legítimo para operar sobre el {id} de otro.

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes operar sobre tu propia cuenta");
    }

    /**
     * ¿El {id} de la URL es el del usuario que trae el token? El id viene
     * firmado dentro del JWT, así que basta comparar enteros: ni consulta a
     * BD, ni depender del email (que el usuario puede cambiar).
     */
    private boolean esElUsuarioAutenticado(int idUrl, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && autenticado.usuarioId() == idUrl;
    }
}
