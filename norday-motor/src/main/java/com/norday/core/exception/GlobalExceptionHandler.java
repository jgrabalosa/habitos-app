package com.norday.core.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ExceptionHandler(RecursoNoEncontradoException.class)
    public ResponseEntity<?> manejarNoEncontrado(RecursoNoEncontradoException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(ConflictoException.class)
    public ResponseEntity<?> manejarConflicto(ConflictoException e) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(RuntimeException.class)
    public ResponseEntity<?> manejarRuntimeException(RuntimeException e) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("mensaje", e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<?> manejarExcepcionNoPrevista(Exception e) {
        log.error("Excepción no controlada", e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("mensaje", "Error interno del servidor"));
    }
}
