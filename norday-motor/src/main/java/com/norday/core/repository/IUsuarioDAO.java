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
     *
     * Devuelve false si la fila ya no existe: por ejemplo, otra transacción
     * la ha borrado (la baja de la cuenta) mientras esta esperaba.
     */
    boolean bloquear(int usuarioId);

    /**
     * Bloquea al usuario (como {@link #bloquear}) y lo devuelve releído de la
     * base de datos, ya con el bloqueo: si ya estaba en la sesión (con
     * open-in-view el controlador lo ha leído antes), sus valores eran los de
     * antes de esperar, y guardarlo entero pisaría lo que otro confirmó
     * entretanto. null si la fila ya no existe; entonces la copia de la
     * sesión, si la había, se suelta para que nadie la guarde.
     */
    Usuario bloquearYReleer(int usuarioId);
    void delete(int id);
}