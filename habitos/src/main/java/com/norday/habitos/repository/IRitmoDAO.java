package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Ritmo;

import java.util.List;

public interface IRitmoDAO {

    void save(Ritmo ritmo);
    Ritmo findByHabito(Habito habito);
    List<Ritmo> findByHabitos(List<Habito> habitos);
    void update(Ritmo ritmo);
    void deleteByHabito(int habitoId);
}
