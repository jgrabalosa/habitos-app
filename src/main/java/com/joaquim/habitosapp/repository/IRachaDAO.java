package com.joaquim.habitosapp.repository;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;

public interface IRachaDAO {

    void save(Racha racha);
    Racha findByHabito(Habito habito);
    void update(Racha racha);
}