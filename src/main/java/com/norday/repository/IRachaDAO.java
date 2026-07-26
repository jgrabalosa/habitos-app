package com.norday.repository;

import com.norday.model.Habito;
import com.norday.model.Racha;

public interface IRachaDAO {

    void save(Racha racha);
    Racha findByHabito(Habito habito);
    void update(Racha racha);
    void deleteByHabito(int habitoId);
}