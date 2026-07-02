package com.joaquim.habitosapp.controller;

import com.joaquim.habitosapp.model.Logro;
import com.joaquim.habitosapp.model.Producto;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioLogro;
import com.joaquim.habitosapp.model.UsuarioProducto;
import com.joaquim.habitosapp.service.LogroService;
import com.joaquim.habitosapp.service.MotorLogrosService;
import com.joaquim.habitosapp.service.ProductoService;
import com.joaquim.habitosapp.service.UsuarioMonedaService;
import com.joaquim.habitosapp.service.UsuarioService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
    public ResponseEntity<?> consultarSaldo(@PathVariable int usuarioId) {
        int saldo = usuarioMonedaService.consultarSaldo(usuarioId);
        return ResponseEntity.ok(Map.of("saldo", saldo));
    }

    @GetMapping("/logros/catalogo")
    public ResponseEntity<List<Logro>> catalogoLogros() {
        return ResponseEntity.ok(logroService.catalogoActivo());
    }

    @GetMapping("/logros/usuario/{usuarioId}")
    public ResponseEntity<?> logrosDeUsuario(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        List<UsuarioLogro> logros = logroService.logrosDeUsuario(usuario);
        return ResponseEntity.ok(logros);
    }

    @GetMapping("/productos/catalogo")
    public ResponseEntity<List<Producto>> catalogoProductos() {
        return ResponseEntity.ok(productoService.catalogoActivo());
    }

    @GetMapping("/productos/usuario/{usuarioId}")
    public ResponseEntity<?> inventarioDeUsuario(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        List<UsuarioProducto> inventario = productoService.inventarioDeUsuario(usuario);
        return ResponseEntity.ok(inventario);
    }

    @PostMapping("/productos/comprar/{usuarioId}/{productoId}")
    public ResponseEntity<?> comprarProducto(@PathVariable int usuarioId, @PathVariable int productoId) {
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

    @PostMapping("/resena/{usuarioId}")
    public ResponseEntity<?> registrarInteraccionResena(@PathVariable int usuarioId) {
        Usuario usuario = usuarioService.buscarPorId(usuarioId);
        if (usuario == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).body("Usuario no encontrado");
        }
        motorLogrosService.evaluarTrasInteraccionResena(usuario);
        return ResponseEntity.ok("Interacción registrada");
    }
}