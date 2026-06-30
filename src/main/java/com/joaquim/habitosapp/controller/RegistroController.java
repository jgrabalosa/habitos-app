package com.joaquim.habitosapp.controller;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Registro;
import com.joaquim.habitosapp.service.HabitoService;
import com.joaquim.habitosapp.service.RegistroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/registros")
public class RegistroController {

    @Autowired
    private RegistroService registroService;

    @Autowired
    private HabitoService habitoService;

    @PostMapping("/completar/{habitoId}")
    public ResponseEntity<?> completar(@PathVariable int habitoId,
                                       @RequestBody(required = false) Map<String, String> body) {
        try {
            Habito habito = habitoService.buscarPorId(habitoId);
            if (habito == null) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body("Hábito no encontrado");
            }
            String nota = body != null ? body.get("nota") : null;
            registroService.completarHabito(habito, nota);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body("Hábito completado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body(e.getMessage());
        }
    }

    @GetMapping("/habito/{habitoId}")
    public ResponseEntity<?> obtenerRegistros(@PathVariable int habitoId) {
        Habito habito = habitoService.buscarPorId(habitoId);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        List<Registro> registros = registroService.obtenerRegistros(habito);
        return ResponseEntity.ok(registros);
    }

    @GetMapping("/habito/{habitoId}/hoy")
    public ResponseEntity<?> estaCompletadoHoy(@PathVariable int habitoId) {
        Habito habito = habitoService.buscarPorId(habitoId);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        boolean completado = registroService.estaCompletadoHoy(habito);
        return ResponseEntity.ok(Map.of("completadoHoy", completado));
    }
    @PutMapping("/{registroId}/nota")
    public ResponseEntity<?> actualizarNota(@PathVariable int registroId,
                                            @RequestBody Map<String, String> body) {
        try {
            String nota = body.get("nota");
            registroService.actualizarNota(registroId, nota);
            return ResponseEntity.ok("Nota actualizada correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}