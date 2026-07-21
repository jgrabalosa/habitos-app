package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Mascota;

public interface IMascotaDAO {
    void save(Mascota mascota);
    void update(Mascota mascota);
    Mascota findByUsuarioId(int usuarioId);
}