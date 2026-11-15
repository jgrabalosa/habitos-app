package com.norday.core.service;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.AppNorday;
import com.norday.core.model.Usuario;
import com.norday.core.model.UsuarioDispositivo;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.repository.IUsuarioDispositivoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.Objects;

/**
 * Los tokens de notificaciones de cada usuario, uno por app del ecosistema
 * (tabla usuario_dispositivo). Quien envía un aviso pide el token de la app
 * a la que va dirigido: un aviso de una app nunca debe acabar en otra.
 *
 * Transición: la columna usuario.fcm_token sigue escribiéndose con el token
 * de {@link AppNorday#HABITOS}, porque es la que lee el jar anterior. Así se
 * puede volver a él sin perder los recordatorios. Este código ya no la lee.
 */
@Service
public class DispositivoService {

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private IUsuarioDispositivoDAO dispositivoDAO;

    /**
     * Guarda el token que manda una app. Un token vacío o ausente deja a esa
     * app sin token, que es lo que hacía antes guardar un null en el usuario.
     */
    @Transactional
    public void registrarToken(int usuarioId, String appId, String fcmToken) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }
        if (fcmToken != null && fcmToken.length() > UsuarioDispositivo.LONGITUD_MAXIMA_TOKEN) {
            throw new IllegalArgumentException("Token FCM demasiado largo");
        }

        if (fcmToken == null || fcmToken.isBlank()) {
            dispositivoDAO.borrar(usuarioId, appId);
        } else {
            dispositivoDAO.guardar(usuarioId, appId, fcmToken);
        }

        if (AppNorday.HABITOS.equals(appId)) {
            usuario.setFcmToken(fcmToken);
            usuarioDAO.update(usuario);
        }
    }

    /** El token de ese usuario en esa app, o null si no tiene. */
    public String tokenDe(int usuarioId, String appId) {
        return dispositivoDAO.tokenDe(usuarioId, appId);
    }

    /** Los tokens de varios usuarios en una app, en una sola consulta. */
    public Map<Integer, String> tokensDe(Collection<Integer> usuarioIds, String appId) {
        return dispositivoDAO.tokensDe(usuarioIds, appId);
    }

    /**
     * FCM ha dado el token por dado de baja (UNREGISTERED). La guía de FCM
     * pide quitarlo del servidor y dejar de enviarle
     * (https://firebase.google.com/docs/cloud-messaging/error-codes). Solo se
     * borra si sigue siendo ese: si la app ya ha registrado uno nuevo, se queda.
     */
    @Transactional
    public void olvidarToken(int usuarioId, String appId, String fcmToken) {
        dispositivoDAO.borrarSiCoincide(usuarioId, appId, fcmToken);

        if (AppNorday.HABITOS.equals(appId)) {
            Usuario usuario = usuarioDAO.findById(usuarioId);
            if (usuario != null && Objects.equals(usuario.getFcmToken(), fcmToken)) {
                usuario.setFcmToken(null);
                usuarioDAO.update(usuario);
            }
        }
    }
}
