package com.norday.core.repository;

import com.norday.core.model.Usuario;
import java.time.LocalDateTime;
import java.util.List;

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
}