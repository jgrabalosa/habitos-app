package com.norday.core.repository;

import com.norday.core.model.UsuarioDispositivo;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IUsuarioDispositivoDAO {

    /** Crea o sustituye el token de ese usuario en esa app. */
    void guardar(int usuarioId, String appId, String fcmToken);

    /** El token de ese usuario en esa app, o null si no tiene. */
    String tokenDe(int usuarioId, String appId);

    /** Los tokens de esos usuarios en esa app, por id; quien no tiene no aparece. */
    Map<Integer, String> tokensDe(Collection<Integer> usuarioIds, String appId);

    /** Borra el token de ese usuario en esa app, sea cual sea. */
    void borrar(int usuarioId, String appId);

    /**
     * Borra el token de ese usuario en esa app solo si sigue siendo ese. Si la
     * app ya ha registrado otro entretanto, el nuevo se queda.
     */
    boolean borrarSiCoincide(int usuarioId, String appId, String fcmToken);

    List<UsuarioDispositivo> findByUsuario(int usuarioId);

    void deleteByUsuario(int usuarioId);
}
