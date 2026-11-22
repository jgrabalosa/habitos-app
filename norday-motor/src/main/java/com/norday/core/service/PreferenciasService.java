package com.norday.core.service;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Set;

/**
 * Preferencias de cuenta: idioma, zona horaria y avisos de la app. Son campos
 * independientes — el idioma no implica la zona ni al revés.
 *
 * Motor: no conoce ningún concepto de dominio.
 */
@Service
public class PreferenciasService {

    /** Idiomas con traducción disponible. Ampliar aquí al añadir uno nuevo. */
    public static final Set<String> IDIOMAS_SOPORTADOS = Set.of("es", "en", "pt");

    @Autowired
    private IUsuarioDAO usuarioDAO;

    public static boolean idiomaValido(String idioma) {
        return idioma != null && IDIOMAS_SOPORTADOS.contains(idioma);
    }

    /** Válida contra la base de datos de zonas del JDK, no contra una lista propia. */
    public static boolean zonaValida(String zonaHoraria) {
        return zonaHoraria != null && ZoneId.getAvailableZoneIds().contains(zonaHoraria);
    }

    /**
     * Un valor de texto del cuerpo del PUT. Ausente o null es «sin cambio»; lo
     * que no sea texto es un error (antes el cuerpo era Map&lt;String, String&gt; y
     * Jackson lo rechazaba igual).
     */
    public static String texto(Object valor, String campo) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof String s) {
            return s;
        }
        throw new IllegalArgumentException("«" + campo + "» debe ser un texto");
    }

    /**
     * Un valor booleano del cuerpo del PUT. Ausente o null es «sin cambio».
     * Se acepta {@code true}/{@code false} y también los textos «true» y
     * «false» (sin distinguir mayúsculas); cualquier otra cosa es un error: un
     * valor raro no debe apagar los avisos por casualidad.
     */
    public static Boolean booleano(Object valor, String campo) {
        if (valor == null) {
            return null;
        }
        if (valor instanceof Boolean b) {
            return b;
        }
        if (valor instanceof String s) {
            if (s.equalsIgnoreCase("true")) {
                return true;
            }
            if (s.equalsIgnoreCase("false")) {
                return false;
            }
        }
        throw new IllegalArgumentException("«" + campo + "» debe ser true o false");
    }

    /**
     * Actualiza las preferencias del usuario. Cada campo es opcional: se
     * envía solo el que se quiere cambiar; null es «sin cambio».
     */
    public Usuario actualizar(int usuarioId, String idioma, String zonaHoraria, Boolean avisosApp) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }

        if (idioma != null) {
            if (!idiomaValido(idioma)) {
                throw new IllegalArgumentException(
                        "Idioma no soportado: " + idioma + ". Válidos: " + IDIOMAS_SOPORTADOS);
            }
            usuario.setIdioma(idioma);
        }

        if (zonaHoraria != null) {
            if (!zonaValida(zonaHoraria)) {
                throw new IllegalArgumentException("Zona horaria no válida: " + zonaHoraria);
            }
            usuario.setZonaHoraria(zonaHoraria);
        }

        if (avisosApp != null) {
            usuario.setAvisosApp(avisosApp);
        }

        usuarioDAO.update(usuario);
        return usuario;
    }
}
