package com.norday.habitos.controller;

import com.norday.core.model.Usuario;
import com.norday.core.security.ControladorAutorizado;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.TextosService;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.OrigenRegistro;
import com.norday.habitos.model.Registro;
import com.norday.habitos.service.HabitoService;
import com.norday.habitos.service.RegistroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/registros")
public class RegistroController extends ControladorAutorizado {

    @Autowired
    private RegistroService registroService;

    @Autowired
    private HabitoService habitoService;

    @Autowired
    private TextosService textosService;

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
        LocalDate fecha = null;
        if (body != null && body.get("fecha") != null && !body.get("fecha").isBlank()) {
            try {
                fecha = LocalDate.parse(body.get("fecha"));
            } catch (DateTimeParseException e) {
                return ResponseEntity.badRequest().body("Formato de fecha inválido, se espera YYYY-MM-DD");
            }
        }
        // Hábitos en minutos. La clave (UUID) evita que un reintento cree otro
        // registro; el origen es MANUAL si no llega. Los minutos se validan en
        // el servicio, que sabe si el día es de minutos: en veces se ignoran.
        Usuario propietario = habito.getPropietario();
        UUID clave = null;
        String textoClave = body != null ? body.get("clave") : null;
        if (textoClave != null && !textoClave.isBlank()) {
            try {
                clave = UUID.fromString(textoClave.trim());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("mensaje", textosService.texto("registro.clave.invalida", propietario)));
            }
        }
        OrigenRegistro origen = OrigenRegistro.MANUAL;
        String textoOrigen = body != null ? body.get("origen") : null;
        if (textoOrigen != null && !textoOrigen.isBlank()) {
            try {
                origen = OrigenRegistro.valueOf(textoOrigen.trim());
            } catch (IllegalArgumentException e) {
                return ResponseEntity.badRequest()
                        .body(Map.of("mensaje", textosService.texto("registro.origen.invalido", propietario)));
            }
        }
        String minutos = body != null ? body.get("minutos") : null;
        Map<String, Object> resultado = registroService.completar(habito,
                new RegistroService.Solicitud(nota, fecha, minutos, clave, origen));
        // Un reintento con una clave ya usada no crea nada: 200 con los datos
        // del registro original y premios a cero, en el mismo formato.
        boolean repetido = Boolean.TRUE.equals(resultado.get("repetido"));
        // LinkedHashMap y no Map.of: minutosDia y minutosObjetivo son null en VECES.
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("mensaje", "Hábito completado correctamente");
        respuesta.put("logrosOtorgados", resultado.get("logros"));
        respuesta.put("puntosGanados", resultado.get("puntosGanados"));
        respuesta.put("registroId", resultado.get("registroId"));
        respuesta.put("mostrarValoracion", resultado.get("mostrarValoracion"));
        respuesta.put("subioNivel", resultado.get("subioNivel"));
        respuesta.put("nivelNuevo", resultado.get("nivelNuevo"));
        respuesta.put("puntosPaso", resultado.get("puntosPaso"));
        respuesta.put("puntosExtraSemana", resultado.get("puntosExtraSemana"));
        respuesta.put("puntosHito", resultado.get("puntosHito"));
        respuesta.put("xpGanada", resultado.get("xpGanada"));
        respuesta.put("minutosDia", resultado.get("minutosDia"));
        respuesta.put("minutosObjetivo", resultado.get("minutosObjetivo"));
        respuesta.put("diaHecho", resultado.get("diaHecho"));
        return ResponseEntity.status(repetido ? HttpStatus.OK : HttpStatus.CREATED).body(respuesta);
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
        return ResponseEntity.ok(registroService.estadoDeHoy(habito));
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

    /** ¿El propietario del hábito de este registro es el usuario que trae el token? */
    private boolean esElPropietario(Habito habito, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && habito.getPropietario() != null
                && habito.getPropietario().getUsuarioId() == autenticado.usuarioId();
    }

    @Override
    protected String mensajeProhibido() {
        return "Solo puedes operar sobre tus propios registros";
    }
}
