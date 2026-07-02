package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Logro;
import java.util.List;

public interface ILogroDAO {
    void save(Logro logro);
    Logro findById(int id);
    List<Logro> findAll();
    List<Logro> findActivos();
    void update(Logro logro);
    void delete(int id);
}