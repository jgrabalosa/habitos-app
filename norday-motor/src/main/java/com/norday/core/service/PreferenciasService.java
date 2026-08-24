package com.norday.core.service;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;
import java.util.Set;

/**
 * Preferencias de cuenta: idioma y zona horaria. Son dos campos
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
     * Actualiza las preferencias del usuario. Cada campo es opcional: se
     * envía solo el que se quiere cambiar.
     */
    public Usuario actualizar(int usuarioId, String idioma, String zonaHoraria) {
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

        usuarioDAO.update(usuario);
        return usuario;
    }
}
