package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Registro;
import java.time.LocalDate;
import java.util.List;

public interface IRegistroDAO {

    void save(Registro registro);
    Registro findById(int id);
    List<Registro> findByHabito(Habito habito);
    Registro findByHabitoAndFecha(Habito habito, LocalDate fecha);
    List<Registro> findByFecha(LocalDate fecha);
    boolean existeRegistroHoy(Habito habito);
    void delete(int id);
    void deleteByHabito(int habitoId);
    List<Registro> findByHabitoAndRango(Habito habito, LocalDate desde, LocalDate hasta);
    void update(Registro registro);
    int contarPorUsuario(int usuarioId);
    int contarConNotaPorUsuario(int usuarioId);
}