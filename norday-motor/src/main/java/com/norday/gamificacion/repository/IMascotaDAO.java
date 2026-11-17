package com.norday.gamificacion.repository;

import com.norday.gamificacion.model.Mascota;

public interface IMascotaDAO {
    void save(Mascota mascota);
    void update(Mascota mascota);

    /**
     * Escribe solo el nombre (un UPDATE de esa columna) y relee la mascota
     * si está en la sesión. Para lo que cambia una sola cosa sin bloquear al
     * usuario: guardar la mascota entera pisaba la XP sumada entretanto.
     */
    void actualizarNombre(int mascotaId, String nombre);

    /** Igual, con la fase elegida. */
    void actualizarFaseElegida(int mascotaId, String faseElegida);
    Mascota findByUsuarioId(int usuarioId);
    void deleteByUsuario(int usuarioId);
}