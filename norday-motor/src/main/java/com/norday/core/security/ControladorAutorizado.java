package com.norday.core.security;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;

/**
 * Base de los controladores que reciben el id del usuario en la URL.
 *
 * Las cinco copias de esElUsuarioAutenticado eran identicas caracter a
 * caracter; los seis prohibido() solo se diferenciaban en la frase. Por eso
 * el mensaje no se impone aqui: cada controlador declara el suyo y la base
 * se limita a envolverlo en el 403.
 *
 * Vive en norday-motor porque UsuarioAutenticado ya vive aqui y el modulo
 * habitos ya depende de este: no hay que tocar ningun pom.
 */
public abstract class ControladorAutorizado {

    /**
     * El id del token contra el de la URL. El email NO sirve para esto: es
     * editable, y compararlo dejaba la sesion inservible en cuanto el
     * usuario lo cambiaba (ver UsuarioAutenticado).
     */
    protected boolean esElUsuarioAutenticado(int idUrl, Authentication authentication) {
        return authentication != null
                && authentication.getPrincipal() instanceof UsuarioAutenticado autenticado
                && autenticado.usuarioId() == idUrl;
    }

    /** La frase concreta del 403 de este controlador. */
    protected abstract String mensajeProhibido();

    protected ResponseEntity<?> prohibido() {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(mensajeProhibido());
    }
}
