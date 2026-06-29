package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;
import com.joaquim.habitosapp.model.Registro;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.List;

@Service
public class RegistroService {

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    public void completarHabito(Habito habito, String nota) {
        if (registroDAO.existeRegistroHoy(habito)) {
            throw new RuntimeException("El hábito ya fue completado hoy");
        }
        Registro registro = new Registro(habito, true, nota);
        registroDAO.save(registro);
        actualizarRacha(habito);
    }

    public void actualizarRacha(Habito habito) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return;

        LocalDate hoy = LocalDate.now();
        LocalDate ayer = hoy.minusDays(1);

        if (racha.getUltimaFecha().equals(ayer)) {
            racha.setRachaActual(racha.getRachaActual() + 1);
        } else if (!racha.getUltimaFecha().equals(hoy)) {
            racha.setRachaActual(1);
        }

        if (racha.getRachaActual() > racha.getRachaMaxima()) {
            racha.setRachaMaxima(racha.getRachaActual());
        }

        racha.setUltimaFecha(hoy);
        rachaDAO.update(racha);
    }

    public boolean estaCompletadoHoy(Habito habito) {
        return registroDAO.existeRegistroHoy(habito);
    }

    public List<Registro> obtenerRegistros(Habito habito) {
        return registroDAO.findByHabito(habito);
    }

    public Registro obtenerPorFecha(Habito habito, LocalDate fecha) {
        return registroDAO.findByHabitoAndFecha(habito, fecha);
    }
}