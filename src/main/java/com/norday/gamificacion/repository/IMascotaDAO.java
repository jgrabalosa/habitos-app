package com.norday.gamificacion.repository;

import com.norday.gamificacion.model.Mascota;

public interface IMascotaDAO {
    void save(Mascota mascota);
    void update(Mascota mascota);
    Mascota findByUsuarioId(int usuarioId);
    void deleteByUsuario(int usuarioId);
}