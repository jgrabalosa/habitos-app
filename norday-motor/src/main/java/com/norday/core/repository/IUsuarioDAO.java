package com.norday.core.repository;

import com.norday.core.model.Usuario;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;

public interface IUsuarioDAO {

    void save(Usuario usuario);
    Usuario findById(int id);
    Usuario findByEmail(String email);
    Usuario findByUsername(String username);
    List<Usuario> findAll();
    void update(Usuario usuario);

    /** Actualiza solo la columna ultimo_acceso, sin cargar la entidad. */
    void actualizarUltimoAcceso(int usuarioId, LocalDateTime ahora);
    void delete(int id);

    /**
     * La columna de transición usuario.fcm_token de esos usuarios, por id;
     * quien la tiene a null no aparece.
     */
    Map<Integer, String> fcmTokensDe(Collection<Integer> usuarioIds);

    /** Pone a null usuario.fcm_token de todo usuario que no sea ese y tenga ese token. */
    void quitarFcmTokenDeOtros(int usuarioId, String fcmToken);
}