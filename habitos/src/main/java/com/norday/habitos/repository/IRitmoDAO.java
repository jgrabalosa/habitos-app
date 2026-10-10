package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Ritmo;

public interface IRitmoDAO {

    void save(Ritmo ritmo);
    Ritmo findByHabito(Habito habito);
    void update(Ritmo ritmo);
    void deleteByHabito(int habitoId);
}
