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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Los tokens de notificaciones de cada usuario, uno por app del ecosistema
 * (tabla usuario_dispositivo). Quien envía un aviso pide el token de la app
 * a la que va dirigido: un aviso de una app nunca debe acabar en otra.
 *
 * Transición: la columna usuario.fcm_token sigue escribiéndose con el token
 * de {@link AppNorday#HABITOS}, porque es la que lee el jar anterior a V24.
 * Y se lee como reserva, solo para esa app y solo si no hay fila: un usuario
 * que registró su token mientras corría el jar anterior lo tiene en la
 * columna y no en la tabla. Un token que cambió durante esa vuelta atrás deja
 * la fila obsoleta, y eso no lo arregla la reserva: hace falta la
 * resincronización de docs/despliegue.md. Por eso volver al jar anterior solo
 * es seguro mientras Norday Focus no esté publicada.
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
     *
     * El token deja de ser de cualquier otro usuario en esa app (cerró sesión
     * y entró otro en el mismo móvil): en la tabla lo hace el DAO, y para
     * {@link AppNorday#HABITOS} también en la columna de transición, que si no
     * lo devolvería la reserva de {@link #tokenDe}.
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
            if (AppNorday.HABITOS.equals(appId)) {
                usuarioDAO.quitarFcmTokenDeOtros(usuarioId, fcmToken);
            }
        }

        if (AppNorday.HABITOS.equals(appId)) {
            usuario.setFcmToken(fcmToken);
            usuarioDAO.update(usuario);
        }
    }

    /**
     * El token de ese usuario en esa app, o null si no tiene. Para
     * {@link AppNorday#HABITOS} sin fila, el de la columna de transición.
     */
    public String tokenDe(int usuarioId, String appId) {
        String token = dispositivoDAO.tokenDe(usuarioId, appId);
        if (token != null || !AppNorday.HABITOS.equals(appId)) {
            return token;
        }
        // Consulta, no findById: un Usuario ya cargado en el contexto no ve el
        // UPDATE masivo de quitarFcmTokenDeOtros y devolvería un token que ya
        // es de otro usuario.
        return usuarioDAO.fcmTokensDe(List.of(usuarioId)).get(usuarioId);
    }

    /**
     * Los tokens de varios usuarios en una app, en una consulta (dos para
     * {@link AppNorday#HABITOS}, con la misma reserva que {@link #tokenDe}).
     */
    public Map<Integer, String> tokensDe(Collection<Integer> usuarioIds, String appId) {
        Map<Integer, String> tokens = new HashMap<>(dispositivoDAO.tokensDe(usuarioIds, appId));
        if (AppNorday.HABITOS.equals(appId)) {
            List<Integer> sinFila = usuarioIds.stream().filter(id -> !tokens.containsKey(id)).toList();
            usuarioDAO.fcmTokensDe(sinFila).forEach(tokens::putIfAbsent);
        }
        return tokens;
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
