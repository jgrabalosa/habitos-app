package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.*;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import com.joaquim.habitosapp.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
public class MotorLogrosService {

    @Autowired
    private LogroService logroService;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    // ── Evento: completar un registro ───────────────────────
    public List<String> evaluarTrasCompletarRegistro(Usuario usuario, Habito habito) {
        List<String> logrosOtorgados = new ArrayList<>();
        logrosOtorgados.addAll(comprobarLogrosDeRacha(usuario, habito));
        logrosOtorgados.addAll(comprobarLogrosDeVolumen(usuario));
        return logrosOtorgados;
    }

    private List<String> comprobarLogrosDeRacha(Usuario usuario, Habito habito) {
        List<String> otorgados = new ArrayList<>();
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return otorgados;

        int actual = racha.getRachaActual();
        int maxima = racha.getRachaMaxima();

        if (actual == 3 && maxima == actual) {
            if (otorgar(usuario, "RACHA_3")) otorgados.add("RACHA_3");
        }
        if (actual == 3 && maxima > actual) {
            if (otorgar(usuario, "RACHA_RECUPERADA")) otorgados.add("RACHA_RECUPERADA");
        }
        if (actual == 7) {
            if (otorgar(usuario, "RACHA_7")) otorgados.add("RACHA_7");
        }
        if (actual == 30) {
            if (otorgar(usuario, "RACHA_30")) otorgados.add("RACHA_30");
        }
        if (actual == 100) {
            if (otorgar(usuario, "RACHA_100")) otorgados.add("RACHA_100");
        }
        if (actual == 365) {
            if (otorgar(usuario, "RACHA_365")) otorgados.add("RACHA_365");
        }
        return otorgados;
    }

    private List<String> comprobarLogrosDeVolumen(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        int totalRegistros = registroDAO.contarPorUsuario(usuario.getUsuarioId());

        if (totalRegistros == 1) {
            if (otorgar(usuario, "PRIMEROS_PASOS")) otorgados.add("PRIMEROS_PASOS");
        }
        if (totalRegistros == 100) {
            if (otorgar(usuario, "REGISTROS_100")) otorgados.add("REGISTROS_100");
        }
        if (totalRegistros == 500) {
            if (otorgar(usuario, "REGISTROS_500")) otorgados.add("REGISTROS_500");
        }
        if (totalRegistros == 1000) {
            if (otorgar(usuario, "REGISTROS_1000")) otorgados.add("REGISTROS_1000");
        }
        return otorgados;
    }

    // ── Evento: crear un hábito ──────────────────────────────
    public List<String> evaluarTrasCrearHabito(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        List<Habito> activos = habitoDAO.findActivos(usuario);

        if (activos.size() == 1) {
            if (otorgar(usuario, "PRIMER_HABITO")) otorgados.add("PRIMER_HABITO");
        }
        if (activos.size() == 3) {
            if (otorgar(usuario, "HABITOS_ACTIVOS_3")) otorgados.add("HABITOS_ACTIVOS_3");
        }
        if (activos.size() == 5) {
            if (otorgar(usuario, "HABITOS_ACTIVOS_5")) otorgados.add("HABITOS_ACTIVOS_5");
        }

        otorgados.addAll(comprobarCategoriasUsadas(usuario, activos));
        return otorgados;
    }

    private List<String> comprobarCategoriasUsadas(Usuario usuario, List<Habito> activos) {
        List<String> otorgados = new ArrayList<>();
        Set<Integer> categoriasDistintas = new HashSet<>();
        for (Habito h : activos) {
            if (h.getTipo() != null) {
                categoriasDistintas.add(h.getTipo().getCategoriaId());
            }
        }

        if (categoriasDistintas.size() == 3) {
            if (otorgar(usuario, "CATEGORIAS_3")) otorgados.add("CATEGORIAS_3");
        }
        if (categoriasDistintas.size() == 5) {
            if (otorgar(usuario, "CATEGORIAS_5")) otorgados.add("CATEGORIAS_5");
        }
        return otorgados;
    }

    // ── Evento: crear categoría personalizada ────────────────
    public void evaluarTrasCrearCategoria(Usuario usuario, List<Categoria> categoriasCreadorUsuario) {
        if (categoriasCreadorUsuario.size() == 1) {
            otorgar(usuario, "PRIMERA_CATEGORIA");
        }
    }

    // ── Evento: login con Google ─────────────────────────────
    public void evaluarTrasLoginGoogle(Usuario usuario) {
        otorgar(usuario, "LOGIN_GOOGLE");
    }

    // ── Evento: actualizar perfil ────────────────────────────
    public void evaluarTrasActualizarPerfil(Usuario usuario) {
        otorgar(usuario, "BIENVENIDO");
    }

    // ── Evento: añadir nota a registro ───────────────────────
    public void evaluarTrasAnadirNota(Usuario usuario) {
        otorgar(usuario, "PRIMERA_NOTA");
    }

    // ── Evento: interacción con reseña (llamado desde Flutter) ──
    public void evaluarTrasInteraccionResena(Usuario usuario) {
        otorgar(usuario, "INTERACCION_RESENA");
    }

    // ── Helper interno ────────────────────────────────────────
    private boolean otorgar(Usuario usuario, String codigo) {
        Logro logro = logroService.buscarPorCodigo(codigo);
        if (logro != null) {
            return logroService.otorgarLogro(usuario, logro.getLogroId());
        }
        return false;
    }
}