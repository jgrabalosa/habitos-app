package com.norday.core.exception;

import com.norday.core.security.UsuarioAutenticado;
import com.norday.core.service.TextosService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import java.util.Locale;
import java.util.Map;

/**
 * Manejo de errores centralizado. Antes cada controlador repetía su propio
 * catch (RuntimeException e) -> 400; esto lo sustituye por un único punto,
 * y además cierra el hueco real que señalaba la auditoría: cualquier
 * excepción NO capturada (un NullPointerException, un fallo de BD) devolvía
 * antes el stack trace completo de Spring al cliente. Ahora siempre se
 * responde con un mensaje genérico y el detalle queda solo en el log del
 * servidor.
 *
 * El cliente Flutter nunca muestra este texto al usuario (ver
 * MensajesError en norday_flutter_core): clasifica el error solo por el
 * código HTTP y siempre enseña un mensaje traducido y genérico. El campo
 * "mensaje" de aquí es para depurar, no para mostrar en pantalla.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @Autowired
    private TextosService textosService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /**
     * Una espera de bloqueo que agotó su tope (lock_timeout, ver
     * IUsuarioDAO.bloquear) o que Postgres cortó por abrazo mortal: otra
     * operación de la misma cuenta estaba en curso. Nada se ha guardado (la
     * transacción se deshizo) y reintentar es lo correcto: 503, con un texto
     * en el idioma del usuario que lo dice. Spring traduce las dos a
     * PessimisticLockingFailureException (CannotAcquireLockException es una
     * de ellas); las de JPA, por si llegan sin traducir.
     */
    @ExceptionHandler({PessimisticLockingFailureException.class,
            jakarta.persistence.PessimisticLockException.class,
            jakarta.persistence.LockTimeoutException.class})
    public ResponseEntity<?> manejarBloqueoAgotado(RuntimeException e) {
        log.warn("Espera de bloqueo agotada: {}", e.getMessage());
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(Map.of("mensaje", textosService.texto("error.bloqueo.reintentar", localeDelAutenticado())));
    }

    /**
     * El idioma del usuario autenticado. Se lee con una consulta suelta y no
     * con la sesión de la petición, que viene de un fallo; sin usuario o si
     * algo falla, el idioma por defecto.
     */
    private Locale localeDelAutenticado() {
        String idioma = null;
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion != null && autenticacion.getPrincipal() instanceof UsuarioAutenticado autenticado) {
            try {
                idioma = jdbcTemplate.queryForObject("SELECT idioma FROM usuario WHERE usuario_id = ?",
                        String.class, autenticado.usuarioId());
            } catch (RuntimeException ignorada) {
                // Sin idioma: el de por defecto.
            }
        }
        return textosService.localeDe(idioma);
    }

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<?> manejarNoEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", mensajeSeguro(e)));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<?> manejarConflicto(ConflictoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", mensajeSeguro(e)));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> manejarRuntimeException(RuntimeException e) {
        // Se loguea a propósito: antes este handler devolvía 400 en silencio y
        // dejaba fallos reales invisibles en el log del servidor.
        log.error("RuntimeException no tipada", e);
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", mensajeSeguro(e)));
    }

    /** Map.of no admite valores null y muchas excepciones no traen mensaje. */
    private String mensajeSeguro(Exception e) {
        return e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> manejarExcepcionNoPrevista(Exception e) {
        log.error("Excepción no controlada", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mensaje", "Error interno del servidor"));
    }
}
