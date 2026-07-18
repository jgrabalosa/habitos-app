package com.joaquim.habitosapp.controller;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.service.HabitoService;
import com.joaquim.habitosapp.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import com.joaquim.habitosapp.model.dto.HabitoDetalleDTO;
import java.time.YearMonth;
import java.util.Map;
import java.util.HashMap;
import com.joaquim.habitosapp.model.dto.DashboardHabitoDTO;

@RestController
@RequestMapping("/api/habitos")
public class HabitoController {

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private UsuarioService usuarioService;

    @GetMapping("/usuario/{usuarioId}")
    public ResponseEntity<?> obtenerTodos(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerTodos(usuario));
    }

    @GetMapping("/usuario/{usuarioId}/activos")
    public ResponseEntity<?> obtenerActivos(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerActivos(usuario));
    }

    @GetMapping("/usuario/{usuarioId}/dashboard")
    public ResponseEntity<?> obtenerDashboard(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Usuario no encontrado");
        }
        return ResponseEntity.ok(habitoService.obtenerDashboard(usuario));
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> buscarPorId(@PathVariable int id) {
        Habito habito = habitoService.buscarPorId(id);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        return ResponseEntity.ok(habito);
    }

    @PostMapping
    public ResponseEntity<?> crear(@RequestBody Habito habito) {
        try {
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
                                        @RequestBody Habito habito) {
        try {
            habito.setHabitoId(id);
            habitoService.actualizar(habito);
            return ResponseEntity.ok("Hábito actualizado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/activar")
    public ResponseEntity<?> activar(@PathVariable int id) {
        try {
            habitoService.activar(id);
            return ResponseEntity.ok("Hábito activado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @PatchMapping("/{id}/desactivar")
    public ResponseEntity<?> desactivar(@PathVariable int id) {
        try {
            habitoService.desactivar(id);
            return ResponseEntity.ok("Hábito desactivado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<?> eliminar(@PathVariable int id) {
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
                                            @RequestParam(required = false) String mes) {
        try {
            YearMonth yearMonth = (mes != null && !mes.isEmpty()) ? YearMonth.parse(mes) : null;
            HabitoDetalleDTO detalle = habitoService.obtenerDetalle(id, yearMonth);
            return ResponseEntity.ok(detalle);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}