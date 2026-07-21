package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Mascota;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.dto.MascotaDTO;
import com.joaquim.habitosapp.model.dto.ResultadoExperienciaDTO;
import com.joaquim.habitosapp.repository.IMascotaDAO;
import com.joaquim.habitosapp.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

@Service
public class MascotaService {

    @Autowired
    private IMascotaDAO mascotaDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    /** Curva provisional: subir a nivel N cuesta 20 + 10×(N-1) XP (afinar más adelante). */
    private int costoNivel(int nivel) {
        return 20 + 10 * (nivel - 1);
    }

    private int xpAcumuladoInicioNivel(int nivel) {
        int total = 0;
        for (int n = 2; n <= nivel; n++) {
            total += costoNivel(n);
        }
        return total;
    }

    private int calcularNivel(int xpTotal) {
        int nivel = 1;
        while (xpTotal >= xpAcumuladoInicioNivel(nivel + 1)) {
            nivel++;
        }
        return nivel;
    }

    private String calcularFase(int nivel) {
        if (nivel <= 2) return "Huevo";
        if (nivel <= 9) return "Cria";
        return "Adulto";
    }

    private String calcularEstado(LocalDate fechaUltimaComida) {
        if (fechaUltimaComida == null) return "dormida";
        long dias = ChronoUnit.DAYS.between(fechaUltimaComida, LocalDate.now());
        if (dias == 0) return "feliz";
        if (dias < 3) return "neutral";
        return "dormida";
    }

    /** Fila creada perezosamente al primer acceso. */
    public Mascota obtenerOCrear(int usuarioId) {
        Mascota mascota = mascotaDAO.findByUsuarioId(usuarioId);
        if (mascota == null) {
            Usuario usuario = usuarioDAO.findById(usuarioId);
            mascota = new Mascota(usuario);
            mascotaDAO.save(mascota);
        }
        return mascota;
    }

    private MascotaDTO construirDTO(Mascota mascota) {
        int nivel = calcularNivel(mascota.getExperiencia());
        int xpInicioNivel = xpAcumuladoInicioNivel(nivel);
        return new MascotaDTO(
                mascota.getNombre(),
                mascota.getExperiencia(),
                nivel,
                mascota.getExperiencia() - xpInicioNivel,
                costoNivel(nivel + 1),
                calcularFase(nivel),
                calcularEstado(mascota.getFechaUltimaComida()),
                mascota.getFechaUltimaComida()
        );
    }

    public MascotaDTO obtenerDTO(int usuarioId) {
        return construirDTO(obtenerOCrear(usuarioId));
    }

    /** El motor no conoce el origen de la XP: los disparadores (completar hábito, usar comida) sí. */
    public ResultadoExperienciaDTO ganarExperiencia(int usuarioId, int cantidad) {
        Mascota mascota = obtenerOCrear(usuarioId);
        int nivelAntes = calcularNivel(mascota.getExperiencia());

        mascota.setExperiencia(mascota.getExperiencia() + cantidad);
        mascotaDAO.update(mascota);

        int nivelDespues = calcularNivel(mascota.getExperiencia());
        boolean subioNivel = nivelDespues > nivelAntes;

        return new ResultadoExperienciaDTO(subioNivel, nivelDespues, construirDTO(mascota));
    }

    public void ponerNombre(int usuarioId, String nuevoNombre) {
        Mascota mascota = obtenerOCrear(usuarioId);
        mascota.setNombre(nuevoNombre);
        mascotaDAO.update(mascota);
    }

    public void registrarComida(int usuarioId) {
        Mascota mascota = obtenerOCrear(usuarioId);
        mascota.setFechaUltimaComida(LocalDate.now());
        mascotaDAO.update(mascota);
    }
}