package com.norday.service;

import com.norday.model.Usuario;

/**
 * Contrato para que cada módulo borre sus propios datos al eliminarse una
 * cuenta. El motor no sabe qué tablas tiene cada dominio: recolecta todas
 * las implementaciones y las invoca antes de borrar el Usuario.
 *
 * Cada módulo nuevo que guarde datos colgando de Usuario debe aportar su
 * propia implementación — así no hace falta acordarse de tocar
 * UsuarioService.eliminarCuenta() cada vez.
 */
public interface LimpiadorDatosUsuario {

    void limpiar(Usuario usuario);
}
