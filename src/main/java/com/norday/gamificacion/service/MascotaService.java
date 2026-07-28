package com.norday.gamificacion.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.dto.MascotaDTO;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.repository.IMascotaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

@Service
public class MascotaService {

    @Autowired
    private IMascotaDAO mascotaDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /** Subir a nivel N cuesta 15×(N-1) XP. */
    private int costoNivel(int nivel) {
        return 15 * (nivel - 1);
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

    /**
     * Devuelve un código, no un texto: el cliente lo traduce, igual que hace
     * con categorías, logros y productos. El servidor no elige idioma.
     */
    private String calcularFase(int nivel) {
        if (nivel <= 2) return "HUEVO";
        if (nivel <= 9) return "CRIA";
        return "ADULTO";
    }

    /**
     * El ánimo sale de cuánto hace que el usuario cumplió todo lo del día.
     * Código, no texto: el cliente lo traduce.
     */
    private String calcularEstado(LocalDate fechaUltimoDiaCompleto, ZoneId zona) {
        if (fechaUltimoDiaCompleto == null) return "triste";
        long dias = ChronoUnit.DAYS.between(fechaUltimoDiaCompleto, LocalDate.now(zona));
        if (dias == 0) return "feliz";
        if (dias < 3) return "dormida";
        return "triste";
    }

    /** La zona del dueño de la mascota: su "hoy" no es el del servidor. */
    private ZoneId zonaDeMascota(Mascota mascota) {
        return zonaUsuarioService.zonaDe(mascota != null ? mascota.getUsuario() : null);
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
                calcularEstado(mascota.getFechaUltimoDiaCompleto(), zonaDeMascota(mascota)),
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

    /**
     * Marca hoy como día cumplido. Genérico a propósito: el motor no sabe qué
     * hace falta cumplir —eso lo decide cada app—, solo que se ha cumplido.
     * Idempotente: si ya estaba marcado hoy, no toca la BD.
     */
    public void registrarDiaCompleto(int usuarioId) {
        Mascota mascota = obtenerOCrear(usuarioId);
        LocalDate hoy = LocalDate.now(zonaDeMascota(mascota));
        if (hoy.equals(mascota.getFechaUltimoDiaCompleto())) return;
        mascota.setFechaUltimoDiaCompleto(hoy);
        mascotaDAO.update(mascota);
    }

    public void registrarComida(int usuarioId) {
        Mascota mascota = obtenerOCrear(usuarioId);
        mascota.setFechaUltimaComida(LocalDate.now(zonaDeMascota(mascota)));
        mascotaDAO.update(mascota);
    }
}