package com.norday.habitos.controller;

import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.dto.HabitoDetalleDTO;
import com.norday.habitos.service.HabitoService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.Map;
import java.util.HashMap;

@RestController
@RequestMapping("/api/habitos")
public class HabitoController {

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obtenerTodos(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerTodos(usuario));
    }

    @GetMapping("/usuario/{usuarioId}/activos")
    public ResponseEntity<?> obtenerActivos(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerActivos(usuario));
    }

    @GetMapping("/usuario/{usuarioId}/resumen")
    public ResponseEntity<?> obtenerResumen(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerResumen(usuario));
    }

    @GetMapping("/usuario/{usuarioId}/dashboard")
    public ResponseEntity<?> obtenerDashboard(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerDashboard(usuario));
    }

    @GetMapping("/usuario/{usuarioId}/semana")
    public ResponseEntity<?> obtenerSemana(@PathVariable int usuarioId,
                                           @RequestParam(required = false) String desde,
                                           Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        try {
            LocalDate fechaDesde = (desde != null && !desde.isBlank()) ? LocalDate.parse(desde) : null;
            return ResponseEntity.ok(habitoService.obtenerSemana(usuario, fechaDesde));
        } catch (DateTimeParseException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("Formato de fecha inválido, se espera YYYY-MM-DD");
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id, Authentication authentication) {
        Habito habito = habitoService.buscarPorId(id);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
        }
        return ResponseEntity.ok(habito);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Habito habito, Authentication authentication) {
        try {
            // El propietario nunca se toma del body: si viene, se ignora. Se
            // fija siempre desde el token, para que nadie pueda crear un
            // hábito a nombre de otro usuario mandando su usuarioId en el JSON.
            Usuario autenticado = usuarioAutenticadoComoUsuario(authentication);
            if (autenticado == null) {
                return prohibido();
            }
            habito.setPropietario(autenticado);
            List<String> logrosOtorgados = habitoService.crearHabito(habito);
            Map<String, Object> respuesta = new HashMap<>();
            respuesta.put("mensaje", "Hábito creado correctamente");
            respuesta.put("logrosOtorgados", logrosOtorgados);
            return ResponseEntity.status(HttpStatus.CREATED).body(respuesta);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public ResponseEntity<?> actualizar(@PathVariable int id,
                                        @RequestBody Habito habito,
                                        Authentication authentication) {
        Habito existente = habitoService.buscarPorId(id);
        if (existente == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(existente, authentication)) {
            return prohibido();
        }
        try {
            habito.setHabitoId(id);
            // Mismo motivo que en crear(): el propietario no se toca desde
            // el body, se conserva el que ya tenía el hábito en BD.
            habito.setPropietario(existente.getPropietario());
            habitoService.actualizar(habito);
            return ResponseEntity.ok("Hábito actualizado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<?> activar(@PathVariable int id, Authentication authentication) {
        Habito habito = habitoService.buscarPorId(id);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
        }
        try {
            habitoService.activar(id);
            return ResponseEntity.ok("Hábito activado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable int id, Authentication authentication) {
        Habito habito = habitoService.buscarPorId(id);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
        }
        try {
            habitoService.desactivar(id);
            return ResponseEntity.ok("Hábito desactivado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id, Authentication authentication) {
        Habito habito = habitoService.buscarPorId(id);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
        }
        try {
            habitoService.eliminar(id);
            return ResponseEntity.ok("Hábito eliminado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/{id}/detalle")
    public ResponseEntity<?> obtenerDetalle(@PathVariable int id,
                                            @RequestParam(required = false) String mes,
                                            Authentication authentication) {
        Habito habito = habitoService.buscarPorId(id);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
        }
        try {
            YearMonth yearMonth = (mes != null && !mes.isEmpty()) ? YearMonth.parse(mes) : null;
            HabitoDetalleDTO detalle = habitoService.obtenerDetalle(id, yearMonth);
            return ResponseEntity.ok(detalle);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }

    // No existe rol de administrador en el proyecto (el token no lleva
    // authorities y Usuario no tiene campo de rol), así que nadie tiene
    // motivo legítimo para operar sobre hábitos ajenos.

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes operar sobre tus propios hábitos");
    }

    /** ¿El {usuarioId} de la URL es el del usuario que trae el token? */
    private boolean esElUsuarioAutenticado(int idUrl, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && autenticado.usuarioId() == idUrl;
    }

    /** ¿El propietario de este hábito es el usuario que trae el token? */
    private boolean esElPropietario(Habito habito, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && habito.getPropietario() != null
                && habito.getPropietario().getUsuarioId() == autenticado.usuarioId();
    }

    /** Carga el Usuario completo a partir del id que trae el token. */
    private Usuario usuarioAutenticadoComoUsuario(Authentication authentication) {
        if (authentication == null || !(authentication.getPrincipal() instanceof UsuarioAutenticado autenticado)) {
            return null;
        }
        return usuarioService.buscarPorId(autenticado.usuarioId());
    }
}
