package com.joaquim.habitosapp.model.dto;

import com.joaquim.habitosapp.model.Usuario;

/**
 * Resultado de un login con Google: además del usuario, indica si la cuenta
 * se acaba de crear en este login (para disparar el mini-onboarding) o ya existía.
 */
public record ResultadoLoginGoogle(Usuario usuario, boolean esNuevo) {
}
