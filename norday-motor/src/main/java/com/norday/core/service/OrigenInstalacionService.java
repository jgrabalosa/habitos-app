package com.norday.core.service;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.Map;

/**
 * Origen de la instalación: de qué red y campaña viene cada usuario, según el
 * Install Referrer de Google Play. Mide el marketing, no identifica a nadie.
 *
 * El primero manda: una vez guardado el origen no se vuelve a escribir, así
 * nadie puede reescribir el suyo después. Un referrer vacío también se guarda
 * (con su fecha): significa «instalación sin campaña» y evita que la app lo
 * reintente en cada apertura.
 *
 * Motor: no conoce ningún concepto de dominio.
 */
@Service
public class OrigenInstalacionService {

    static final int MAX_REFERRER = 500;
    static final int MAX_UTM = 100;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    /**
     * @return true si se guardó el origen; false si el usuario ya tenía uno
     *         (en ese caso no se cambia nada).
     */
    public boolean registrar(int usuarioId, String referrer) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }
        if (usuario.getReferrerFecha() != null) {
            return false;
        }

        String crudo = referrer == null ? "" : referrer.trim();
        Map<String, String> parametros = extraerParametros(crudo);

        usuario.setReferrerCrudo(recortar(crudo, MAX_REFERRER));
        usuario.setUtmSource(recortarONulo(parametros.get("utm_source"), MAX_UTM));
        usuario.setUtmMedium(recortarONulo(parametros.get("utm_medium"), MAX_UTM));
        usuario.setUtmCampaign(recortarONulo(parametros.get("utm_campaign"), MAX_UTM));
        usuario.setReferrerFecha(LocalDateTime.now(ZoneOffset.UTC));
        usuarioDAO.update(usuario);
        return true;
    }

    /** Formato clave=valor&clave=valor, URL-decodificado. Si una clave se repite, gana la primera. */
    public static Map<String, String> extraerParametros(String crudo) {
        Map<String, String> resultado = new HashMap<>();
        if (crudo == null || crudo.isBlank()) {
            return resultado;
        }
        for (String par : crudo.split("&")) {
            int igual = par.indexOf('=');
            if (igual <= 0) {
                continue;
            }
            String clave = decodificar(par.substring(0, igual)).trim();
            String valor = decodificar(par.substring(igual + 1)).trim();
            if (!clave.isEmpty() && !valor.isEmpty()) {
                resultado.putIfAbsent(clave, valor);
            }
        }
        return resultado;
    }

    private static String decodificar(String texto) {
        try {
            return URLDecoder.decode(texto, StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            // Escape % mal formado: se queda tal cual, sin romper la petición.
            return texto;
        }
    }

    private static String recortar(String texto, int max) {
        return texto.length() > max ? texto.substring(0, max) : texto;
    }

    private static String recortarONulo(String texto, int max) {
        return texto == null ? null : recortar(texto, max);
    }
}
