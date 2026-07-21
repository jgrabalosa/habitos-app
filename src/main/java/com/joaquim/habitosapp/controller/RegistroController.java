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
            Map<String, Object> resultado = registroService.completarHabito(habito, nota);
            return ResponseEntity.status(HttpStatus.CREATED)
                    .body(Map.of(
                            "mensaje", "Hábito completado correctamente",
                            "logrosOtorgados", resultado.get("logros"),
                            "puntosGanados", resultado.get("puntosGanados"),
                            "registroId", resultado.get("registroId"),
                            "mostrarValoracion", resultado.get("mostrarValoracion"),
                            "subioNivel", resultado.get("subioNivel"),
                            "nivelNuevo", resultado.get("nivelNuevo")
                    ));
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
        int completadosPeriodo = registroService.contarCompletadosPeriodoActual(habito);
        return ResponseEntity.ok(Map.of(
                "completadoHoy", completado,
                "completadosPeriodo", completadosPeriodo,
                "meta", habito.getMeta()
        ));
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

    @PutMapping("/{registroId}/valoracion")
    public ResponseEntity<?> actualizarValoracion(@PathVariable int registroId,
                                                  @RequestBody Map<String, Integer> body) {
        try {
            Integer valoracion = body.get("valoracion");
            registroService.actualizarValoracion(registroId, valoracion);
            return ResponseEntity.ok("Valoración guardada correctamente");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body(e.getMessage());
        }
    }
}