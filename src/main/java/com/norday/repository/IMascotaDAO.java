package com.norday.repository;

import com.norday.model.Mascota;

public interface IMascotaDAO {
    void save(Mascota mascota);
    void update(Mascota mascota);
    Mascota findByUsuarioId(int usuarioId);
    void deleteByUsuario(int usuarioId);
}