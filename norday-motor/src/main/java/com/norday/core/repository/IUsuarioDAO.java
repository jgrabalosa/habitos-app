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

    /**
     * Bloquea la fila del usuario hasta el final de la transacción en curso:
     * otra transacción que pida el mismo bloqueo espera a que esta termine.
     * Para lo que lee "antes" y "después" de algo del usuario entero (su
     * saldo, sus logros) y no puede dejar que otra operación se cuele en
     * medio. Quien bloquee también algo que cuelga del usuario, lo bloquea
     * después del usuario, nunca antes.
     */
    void bloquear(int usuarioId);
    void delete(int id);
}