package com.norday.core.security;

import org.springframework.security.core.AuthenticatedPrincipal;

/**
 * Principal de una petición autenticada, construido a partir del JWT.
 *
 * El {@code usuarioId} es lo único que se usa para autorizar: es la clave
 * primaria, no cambia nunca. El email va también porque es el subject del
 * token y sirve para logs, pero <b>no debe usarse para autorizar</b> — es
 * editable, y compararlo dejaba la sesión inservible en cuanto el usuario
 * cambiaba su email.
 *
 * Implementa AuthenticatedPrincipal para que {@code Authentication.getName()}
 * siga devolviendo el email, como antes de meter el id como claim.
 */
public record UsuarioAutenticado(int usuarioId, String email)
        implements AuthenticatedPrincipal {

    @Override
    public String getName() {
        return email;
    }
}
