package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;
import java.time.LocalDate;

@Service
public class HabitoService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    public void crearHabito(Habito habito) {
        habito.setFechaInicio(LocalDate.now());
        habito.setActivo(true);
        habitoDAO.save(habito);
        Racha racha = new Racha(habito);
        rachaDAO.save(racha);
    }

    public Habito buscarPorId(int id) {
        return habitoDAO.findById(id);
    }

    public List<Habito> obtenerTodos(Usuario propietario) {
        return habitoDAO.findByPropietario(propietario);
    }

    public List<Habito> obtenerActivos(Usuario propietario) {
        return habitoDAO.findActivos(propietario);
    }

    public List<Habito> obtenerInactivos(Usuario propietario) {
        return habitoDAO.findInactivos(propietario);
    }

    public void actualizar(Habito habito) {
        habitoDAO.update(habito);
    }

    public void activar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito != null) {
            habito.setActivo(true);
            habitoDAO.update(habito);
        }
    }

    public void desactivar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito != null) {
            habito.setActivo(false);
            habitoDAO.update(habito);
        }
    }

    public void eliminar(int id) {
        habitoDAO.delete(id);
    }
}