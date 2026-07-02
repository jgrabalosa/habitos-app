package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;
import com.joaquim.habitosapp.model.Registro;
import com.joaquim.habitosapp.model.Usuario;
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

    @Autowired
    private MotorLogrosService motorLogrosService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    private static final int PUNTOS_HABITO_COMPLETADO = 100; // provisional

    public void completarHabito(Habito habito, String nota) {
        if (registroDAO.existeRegistroHoy(habito)) {
            throw new RuntimeException("El hábito ya fue completado hoy");
        }
        Registro registro = new Registro(habito, true, nota);
        registroDAO.save(registro);
        actualizarRacha(habito);

        Usuario usuario = habito.getPropietario();

        usuarioMonedaService.registrarMovimiento(
                usuario, PUNTOS_HABITO_COMPLETADO, "HABITO_COMPLETADO",
                habito.getHabitoId(), "Hábito completado: " + habito.getNombre()
        );

        otorgarPuntosPorHitoRacha(usuario, habito);
        motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);
    }

    private void otorgarPuntosPorHitoRacha(Usuario usuario, Habito habito) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return;

        int actual = racha.getRachaActual();
        int puntos = switch (actual) {
            case 3 -> 20;
            case 7 -> 50;
            case 30 -> 200;
            case 100 -> 500;
            case 365 -> 1000;
            default -> 0;
        };

        if (puntos > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, puntos, "HITO_RACHA", habito.getHabitoId(),
                    "Hito de racha (" + actual + ") en: " + habito.getNombre()
            );
        }
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

    public void actualizarNota(int registroId, String nota) {
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RuntimeException("Registro no encontrado");
        }
        registro.setNota(nota);
        registroDAO.update(registro);

        Usuario usuario = registro.getHabito().getPropietario();
        motorLogrosService.evaluarTrasAnadirNota(usuario);
    }
}