package com.norday.gamificacion.controller;

import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.UsuarioService;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MotorLogrosService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/gamificacion")
public class GamificacionController {

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private LogroService logroService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private MotorLogrosService motorLogrosService;

    @GetMapping("/saldo/{usuarioId}")
    public ResponseEntity<?> consultarSaldo(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        int saldo = usuarioMonedaService.consultarSaldo(usuarioId);
        return ResponseEntity.ok(Map.of("saldo", saldo));
    }

    /**
     * La cabecera es opcional a propósito: si falta, catálogo completo. La app
     * que está en test cerrado todavía no la manda y no puede quedarse sin
     * logros por eso.
     */
    @GetMapping("/logros/catalogo")
    public ResponseEntity<List<Logro>> catalogoLogros(
            @RequestHeader(value = "X-Norday-App", required = false) String appId) {
        return ResponseEntity.ok(logroService.catalogoActivo(appId));
    }

    @GetMapping("/logros/usuario/{usuarioId}")
    public ResponseEntity<?> logrosDeUsuario(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        List<UsuarioLogro> logros = logroService.logrosDeUsuario(usuario);
        return ResponseEntity.ok(logros);
    }

    /** Misma cabecera opcional que en el catálogo de logros. */
    @GetMapping("/productos/catalogo")
    public ResponseEntity<List<Producto>> catalogoProductos(
            @RequestHeader(value = "X-Norday-App", required = false) String appId) {
        return ResponseEntity.ok(productoService.catalogoActivo(appId));
    }

    @GetMapping("/productos/usuario/{usuarioId}")
    public ResponseEntity<?> inventarioDeUsuario(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        List<UsuarioProducto> inventario = productoService.inventarioDeUsuario(usuario);
        return ResponseEntity.ok(inventario);
    }

    @PostMapping("/productos/comprar/{usuarioId}/{productoId}")
    public ResponseEntity<?> comprarProducto(@PathVariable int usuarioId, @PathVariable int productoId,
                                             Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        try {
            productoService.comprarProducto(usuario, productoId);
            return ResponseEntity.ok("Producto comprado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/productos/otorgar/{usuarioId}/{productoId}")
    public ResponseEntity<?> otorgarProducto(@PathVariable int usuarioId, @PathVariable int productoId,
                                             Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        // "Otorgar" es un regalo: a diferencia de comprarProducto, nunca cobra
        // monedas. El único uso real hoy es SelectorAvatarGratis (elegir un
        // avatar gratis en el onboarding), así que se restringe a esa
        // categoría; si no, cualquiera podría autorregalarse cualquier
        // producto de pago, temas incluidos, sin gastar nada.
        Producto producto = productoService.buscarPorId(productoId);
        if (producto == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Producto no encontrado");
        }
        if (!"Avatar".equals(producto.getCategoria())) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN)
                    .body("Solo los avatares pueden otorgarse gratis por esta vía");
        }
        try {
            productoService.otorgarProducto(usuario, productoId);
            return ResponseEntity.ok("Producto otorgado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/productos/equipar/{usuarioId}/{productoId}")
    public ResponseEntity<?> equiparProducto(@PathVariable int usuarioId, @PathVariable int productoId,
                                             Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        try {
            productoService.equiparProducto(usuario, productoId);
            return ResponseEntity.ok("Producto equipado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/resena/{usuarioId}")
    public ResponseEntity<?> registrarInteraccionResena(@PathVariable int usuarioId, Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        motorLogrosService.evaluarTrasInteraccionResena(usuario);
        return ResponseEntity.ok("Interacción registrada");
    }

    @PostMapping("/productos/desequipar/{usuarioId}/{productoId}")
    public ResponseEntity<?> desequiparProducto(@PathVariable int usuarioId, @PathVariable int productoId,
                                                Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        try {
            productoService.desequiparProducto(usuario, productoId);
            return ResponseEntity.ok("Producto desequipado correctamente");
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    @PostMapping("/productos/usar/{usuarioId}/{productoId}")
    public ResponseEntity<?> usarProducto(@PathVariable int usuarioId, @PathVariable int productoId,
                                          Authentication authentication) {
        if (!esElUsuarioAutenticado(usuarioId, authentication)) {
            return prohibido();
        }
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        try {
            Map<String, Object> resultado = productoService.usarProducto(usuario, productoId);
            return ResponseEntity.ok(resultado);
        } catch (RuntimeException e) {
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(e.getMessage());
        }
    }

    private ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body("Solo puedes operar sobre tu propia cuenta");
    }

    private boolean esElUsuarioAutenticado(int idUrl, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && autenticado.usuarioId() == idUrl;
    }
}
