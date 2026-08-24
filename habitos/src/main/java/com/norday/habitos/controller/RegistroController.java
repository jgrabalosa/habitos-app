package com.norday.habitos.controller;

import com.norday.core.security.UsuarioAutenticado;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RegistroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
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
                                       @RequestBody(required = false) Map<String, String> body,
                                       Authentication authentication) {
        Habito habito = habitoService.buscarPorId(habitoId);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
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
    }

    @GetMapping("/habito/{habitoId}")
    public ResponseEntity<?> obtenerRegistros(@PathVariable int habitoId, Authentication authentication) {
        Habito habito = habitoService.buscarPorId(habitoId);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
        }
        List<Registro> registros = registroService.obtenerRegistros(habito);
        return ResponseEntity.ok(registros);
    }

    @GetMapping("/habito/{habitoId}/hoy")
    public ResponseEntity<?> estaCompletadoHoy(@PathVariable int habitoId, Authentication authentication) {
        Habito habito = habitoService.buscarPorId(habitoId);
        if (habito == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body("Hábito no encontrado");
        }
        if (!esElPropietario(habito, authentication)) {
            return prohibido();
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
                                            @RequestBody Map<String, String> body,
                                            Authentication authentication) {
        Registro registro = registroService.buscarPorId(registroId);
        if (registro == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Registro no encontrado");
        }
        if (!esElPropietario(registro.getHabito(), authentication)) {
            return prohibido();
        }
        String nota = body.get("nota");
        registroService.actualizarNota(registroId, nota);
        return ResponseEntity.ok("Nota actualizada correctamente");
    }

    @PutMapping("/{registroId}/valoracion")
    public ResponseEntity<?> actualizarValoracion(@PathVariable int registroId,
                                                  @RequestBody Map<String, Integer> body,
                                                  Authentication authentication) {
        Registro registro = registroService.buscarPorId(registroId);
        if (registro == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Registro no encontrado");
        }
        if (!esElPropietario(registro.getHabito(), authentication)) {
            return prohibido();
        }
        Integer valoracion = body.get("valoracion");
        registroService.actualizarValoracion(registroId, valoracion);
        return ResponseEntity.ok("Valoración guardada correctamente");
    }

    @DeleteMapping("/{registroId}")
    public ResponseEntity<?> deshacer(@PathVariable int registroId, Authentication authentication) {
        Registro registro = registroService.buscarPorId(registroId);
        if (registro == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Registro no encontrado");
        }
        if (!esElPropietario(registro.getHabito(), authentication)) {
            return prohibido();
        }
        Map<String, Object> resultado = registroService.deshacerRegistro(registroId);
        return ResponseEntity.ok(resultado);
    }

    // No existe rol de administrador en el proyecto, así que nadie tiene
    // motivo legítimo para operar sobre registros ajenos.

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes operar sobre tus propios registros");
    }

    /** ¿El propietario del hábito de este registro es el usuario que trae el token? */
    private boolean esElPropietario(Habito habito, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && habito.getPropietario() != null
                && habito.getPropietario().getUsuarioId() == autenticado.usuarioId();
    }
}
