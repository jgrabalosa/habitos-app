package com.norday.enfoque.controller;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.TextosService;
import com.norday.core.service.UsuarioService;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.model.dto.PeticionSesionDTO;
import com.norday.enfoque.service.SesionEnfoqueService;
import com.norday.enfoque.service.SesionEnfoqueService.Aporte;
import com.norday.enfoque.service.SesionEnfoqueService.Pagina;
import com.norday.enfoque.service.SesionEnfoqueService.Resultado;
import com.norday.enfoque.service.SesionEnfoqueService.Resumen;
import com.norday.enfoque.service.SesionInvalidaException;
import com.norday.gamificacion.model.DestinoDisponible;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * La API de Norday Focus. El usuario sale siempre del token (no va en la
 * URL), como en las rutas de conocimiento: nadie puede pedir ni escribir las
 * sesiones de otro. Sin token, 401 (SecurityConfig).
 *
 * Fechas: los instantes van en ISO-8601 UTC con Z ("2026-11-15T09:00:00Z") y
 * los días como "YYYY-MM-DD", en la zona del usuario.
 */
@RestController
@RequestMapping("/api/enfoque")
public class EnfoqueController {

    @Autowired
    private SesionEnfoqueService sesionEnfoqueService;

    @Autowired
    private UsuarioService usuarioService;

    @Autowired
    private TextosService textosService;

    /** 200 {"destinos":[{tipo, id, nombre, minutosDia, minutosObjetivo}]}. */
    @GetMapping("/destinos")
    public ResponseEntity<?> destinos(Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        List<Map<String, Object>> destinos = sesionEnfoqueService.destinos(usuario).stream()
                .map(EnfoqueController::destino)
                .collect(Collectors.toList());
        return ResponseEntity.ok(Map.of("destinos", destinos));
    }

    /**
     * 201 {sesion, aporte} si la sesión es nueva; 200 con el mismo formato si
     * la clave ya estaba guardada con el mismo cuerpo; 409 si estaba con otro
     * cuerpo o si se solapa con otra sesión; 400 si no cumple las reglas.
     */
    @PostMapping("/sesiones")
    public ResponseEntity<?> registrar(@RequestBody(required = false) PeticionSesionDTO peticion,
                                       Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        Resultado resultado = sesionEnfoqueService.registrar(usuario, peticion);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("sesion", sesion(resultado.sesion()));
        respuesta.put("aporte", aporte(resultado.aporte(), usuario));
        return ResponseEntity.status(resultado.nueva() ? HttpStatus.CREATED : HttpStatus.OK).body(respuesta);
    }

    /** 200 {dias:[{dia, minutos, sesiones}], totalMinutos, totalSesiones, rachaActual}. */
    @GetMapping("/resumen")
    public ResponseEntity<?> resumen(@RequestParam(required = false) String desde,
                                     @RequestParam(required = false) String hasta,
                                     Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        Resumen resumen = sesionEnfoqueService.resumen(usuario, desde, hasta);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("dias", resumen.dias().stream().map(dia -> {
            Map<String, Object> mapa = new LinkedHashMap<>();
            mapa.put("dia", dia.dia().toString());
            mapa.put("minutos", dia.minutos());
            mapa.put("sesiones", dia.sesiones());
            return mapa;
        }).collect(Collectors.toList()));
        respuesta.put("totalMinutos", resumen.totalMinutos());
        respuesta.put("totalSesiones", resumen.totalSesiones());
        respuesta.put("rachaActual", resumen.rachaActual());
        return ResponseEntity.ok(respuesta);
    }

    /** 200 {sesiones:[{…sesion, aporte:{estado, motivo, motivoTexto, minutos}}], pagina, tamano, total, hayMas}. */
    @GetMapping("/sesiones")
    public ResponseEntity<?> historial(@RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "20") int tamano,
                                       Authentication authentication) {
        Usuario usuario = usuarioAutenticado(authentication);
        Pagina resultado = sesionEnfoqueService.historial(usuario, pagina, tamano);
        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("sesiones", resultado.sesiones().stream().map(s -> {
            Map<String, Object> mapa = sesion(s);
            Map<String, Object> aporte = new LinkedHashMap<>();
            aporte.put("estado", s.getAporteEstado().name());
            aporte.put("motivo", s.getAporteMotivo());
            aporte.put("motivoTexto", motivoTexto(s.getAporteMotivo(), usuario));
            aporte.put("minutos", s.getMinutosAportados());
            mapa.put("aporte", aporte);
            return mapa;
        }).collect(Collectors.toList()));
        respuesta.put("pagina", resultado.pagina());
        respuesta.put("tamano", resultado.tamano());
        respuesta.put("total", resultado.total());
        respuesta.put("hayMas", resultado.hayMas());
        return ResponseEntity.ok(respuesta);
    }

    /**
     * Las reglas que incumple una petición son error del cliente: 400 con el
     * texto traducido, sin la línea de error que deja en el log el manejador
     * genérico de RuntimeException.
     */
    @ExceptionHandler(SesionInvalidaException.class)
    public ResponseEntity<?> peticionInvalida(SesionInvalidaException e) {
        return ResponseEntity.badRequest().body(Map.of("mensaje", e.getMessage()));
    }

    // ── Formatos de respuesta ────────────────────────────────────

    private static Map<String, Object> destino(DestinoDisponible destino) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("tipo", destino.tipo());
        mapa.put("id", destino.id());
        mapa.put("nombre", destino.nombre());
        mapa.put("minutosDia", destino.minutosDia());
        mapa.put("minutosObjetivo", destino.minutosObjetivo());
        return mapa;
    }

    private static Map<String, Object> sesion(SesionEnfoque sesion) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("id", sesion.getId());
        mapa.put("clave", sesion.getClave().toString());
        mapa.put("inicioUtc", sesion.getInicioUtc().toInstant(ZoneOffset.UTC).toString());
        mapa.put("duracionSeg", sesion.getDuracionSeg());
        mapa.put("duracionObjetivoMin", sesion.getDuracionObjetivoMin());
        mapa.put("estado", sesion.getEstado().name());
        mapa.put("dia", sesion.getDia().toString());
        mapa.put("minutos", sesion.minutos());
        if (sesion.getDestinoTipo() != null) {
            Map<String, Object> destino = new LinkedHashMap<>();
            destino.put("tipo", sesion.getDestinoTipo());
            destino.put("id", sesion.getDestinoRef());
            mapa.put("destino", destino);
        } else {
            mapa.put("destino", null);
        }
        mapa.put("creadoEn", sesion.getCreadoEn().toInstant(ZoneOffset.UTC).toString());
        return mapa;
    }

    private Map<String, Object> aporte(Aporte aporte, Usuario usuario) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("estado", aporte.estado().name());
        mapa.put("motivo", aporte.motivo());
        mapa.put("motivoTexto", motivoTexto(aporte.motivo(), usuario));
        mapa.put("minutos", aporte.minutos());
        mapa.put("monedas", aporte.monedas());
        mapa.put("xp", aporte.xp());
        mapa.put("minutosDia", aporte.minutosDia());
        mapa.put("minutosObjetivo", aporte.minutosObjetivo());
        mapa.put("diaHecho", aporte.diaHecho());
        mapa.put("logros", aporte.logros());
        return mapa;
    }

    /** El motivo en el idioma del usuario; los motivos son claves de los bundles. */
    private String motivoTexto(String motivo, Usuario usuario) {
        return motivo == null ? null : textosService.texto(motivo, usuario);
    }

    private Usuario usuarioAutenticado(Authentication authentication) {
        UsuarioAutenticado autenticado = (UsuarioAutenticado) authentication.getPrincipal();
        Usuario usuario = usuarioService.buscarPorId(autenticado.usuarioId());
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }
        return usuario;
    }
}
