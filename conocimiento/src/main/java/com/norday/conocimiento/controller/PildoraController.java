package com.norday.conocimiento.controller;

import com.norday.conocimiento.model.dto.PildoraDetalleDTO;
import com.norday.conocimiento.model.dto.PildoraPreviewDTO;
import com.norday.conocimiento.service.BucleService;
import com.norday.conocimiento.service.PildoraService;
import com.norday.conocimiento.service.ValoracionService;
import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/conocimiento/pildoras")
public class PildoraController {

    @Autowired
    private BucleService bucleService;

    @Autowired
    private PildoraService pildoraService;

    @Autowired
    private ValoracionService valoracionService;

    @Autowired
    private UsuarioService usuarioService;

    /**
     * `hayContenido` viaja aparte porque el cliente necesita distinguir "aún
     * no ha llegado" de "no queda nada": la pantalla vacía se pinta distinto.
     */
    @GetMapping("/siguiente")
    public ResponseEntity<?> siguiente(Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        PildoraPreviewDTO pildora = bucleService.obtenerSiguientePildora(usuario);

        Map<String, Object> respuesta = new HashMap<>();
        respuesta.put("pildora", pildora);
        respuesta.put("hayContenido", pildora != null);
        return ResponseEntity.ok(respuesta);
    }

    @PostMapping("/{id}/match")
    public ResponseEntity<?> match(@PathVariable int id, Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        PildoraDetalleDTO detalle = pildoraService.match(usuario, id);
        return ResponseEntity.ok(detalle);
    }

    @PostMapping("/{id}/descartar")
    public ResponseEntity<?> descartar(@PathVariable int id, Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        pildoraService.descartar(usuario, id);
        return ResponseEntity.ok().build();
    }

    @PutMapping("/{id}/guardar")
    public ResponseEntity<?> guardar(@PathVariable int id,
                                     @RequestBody Map<String, Boolean> body,
                                     Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        Boolean guardar = body.get("guardar");
        if (guardar == null) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body("Falta el campo 'guardar'");
        }
        pildoraService.guardar(usuario, id, guardar);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/{id}/valoracion")
    public ResponseEntity<?> valorar(@PathVariable int id,
                                     @RequestBody Map<String, Object> body,
                                     Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);

        Object puntuacion = body.get("puntuacion");
        if (!(puntuacion instanceof Integer)) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                    .body("La puntuación debe estar entre 1 y 5");
        }
        Object nota = body.get("notaPersonal");

        valoracionService.valorar(usuario, id, (Integer) puntuacion,
                nota != null ? nota.toString() : null);
        return ResponseEntity.ok().build();
    }

    @GetMapping("/coleccion")
    public ResponseEntity<?> coleccion(@RequestParam(required = false) Integer categoriaId,
                                       @RequestParam(required = false) String estado,
                                       Authentication authentication) {
        try {
            Usuario usuario = usuarioAutenticado(authentication);
            return ResponseEntity.ok(pildoraService.obtenerColeccion(usuario, categoriaId, estado));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private Usuario usuarioAutenticado(Authentication authentication) {
        UsuarioAutenticado autenticado = (UsuarioAutenticado) authentication.getPrincipal();
        return usuarioService.buscarPorId(autenticado.usuarioId());
    }
}
