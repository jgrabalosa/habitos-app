package com.norday.model.dto;

import com.norday.model.Usuario;

/**
 * Resultado de un login con Google: además del usuario, indica si la cuenta
 * se acaba de crear en este login (para disparar el mini-onboarding) o ya existía.
 */
public record ResultadoLoginGoogle(Usuario usuario, boolean esNuevo) {
}
