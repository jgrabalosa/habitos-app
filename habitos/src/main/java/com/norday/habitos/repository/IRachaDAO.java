package com.norday.habitos.repository;

import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;

public interface IRachaDAO {

    void save(Racha racha);
    Racha findByHabito(Habito habito);
    void update(Racha racha);
    void deleteByHabito(int habitoId);
}