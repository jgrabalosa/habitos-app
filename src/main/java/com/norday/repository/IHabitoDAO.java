package com.norday.repository;

import com.norday.model.Habito;
import com.norday.model.Usuario;
import java.util.List;

public interface IHabitoDAO {

    void save(Habito habito);
    Habito findById(int id);
    List<Habito> findByPropietario(Usuario propietario);
    List<Habito> findActivos(Usuario propietario);
    List<Habito> findInactivos(Usuario propietario);
    void update(Habito habito);
    void delete(int id);
    List<Habito> findTodosActivos();
}