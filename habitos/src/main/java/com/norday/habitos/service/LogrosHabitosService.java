package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.service.LogroService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Logros específicos del dominio "hábitos": rachas, volumen de registros,
 * hábitos activos, categorías y notas. El otorgado en sí lo hace el motor
 * genérico (LogroService); aquí solo vive la decisión de qué código toca.
 */
@Service
public class LogrosHabitosService {

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
            if (logroService.otorgarSiNoTiene(usuario, "RACHA_3")) otorgados.add("RACHA_3");
        }
        if (actual == 3 && maxima > actual) {
            if (logroService.otorgarSiNoTiene(usuario, "RACHA_RECUPERADA")) otorgados.add("RACHA_RECUPERADA");
        }
        if (actual == 7) {
            if (logroService.otorgarSiNoTiene(usuario, "RACHA_7")) otorgados.add("RACHA_7");
        }
        if (actual == 30) {
            if (logroService.otorgarSiNoTiene(usuario, "RACHA_30")) otorgados.add("RACHA_30");
        }
        if (actual == 100) {
            if (logroService.otorgarSiNoTiene(usuario, "RACHA_100")) otorgados.add("RACHA_100");
        }
        if (actual == 365) {
            if (logroService.otorgarSiNoTiene(usuario, "RACHA_365")) otorgados.add("RACHA_365");
        }
        return otorgados;
    }

    private List<String> comprobarLogrosDeVolumen(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        int totalRegistros = registroDAO.contarPorUsuario(usuario.getUsuarioId());

        if (totalRegistros == 1) {
            if (logroService.otorgarSiNoTiene(usuario, "PRIMEROS_PASOS")) otorgados.add("PRIMEROS_PASOS");
        }
        if (totalRegistros == 100) {
            if (logroService.otorgarSiNoTiene(usuario, "REGISTROS_100")) otorgados.add("REGISTROS_100");
        }
        if (totalRegistros == 500) {
            if (logroService.otorgarSiNoTiene(usuario, "REGISTROS_500")) otorgados.add("REGISTROS_500");
        }
        if (totalRegistros == 1000) {
            if (logroService.otorgarSiNoTiene(usuario, "REGISTROS_1000")) otorgados.add("REGISTROS_1000");
        }
        return otorgados;
    }

    // ── Evento: crear un hábito ──────────────────────────────
    public List<String> evaluarTrasCrearHabito(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        List<Habito> activos = habitoDAO.findActivos(usuario);

        if (activos.size() == 1) {
            if (logroService.otorgarSiNoTiene(usuario, "PRIMER_HABITO")) otorgados.add("PRIMER_HABITO");
        }
        if (activos.size() == 3) {
            if (logroService.otorgarSiNoTiene(usuario, "HABITOS_ACTIVOS_3")) otorgados.add("HABITOS_ACTIVOS_3");
        }
        if (activos.size() == 5) {
            if (logroService.otorgarSiNoTiene(usuario, "HABITOS_ACTIVOS_5")) otorgados.add("HABITOS_ACTIVOS_5");
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
            if (logroService.otorgarSiNoTiene(usuario, "CATEGORIAS_3")) otorgados.add("CATEGORIAS_3");
        }
        if (categoriasDistintas.size() == 5) {
            if (logroService.otorgarSiNoTiene(usuario, "CATEGORIAS_5")) otorgados.add("CATEGORIAS_5");
        }
        return otorgados;
    }

    // ── Evento: crear categoría personalizada ────────────────
    public void evaluarTrasCrearCategoria(Usuario usuario, List<Categoria> categoriasCreadorUsuario) {
        if (categoriasCreadorUsuario.size() == 1) {
            logroService.otorgarSiNoTiene(usuario, "PRIMERA_CATEGORIA");
        }
    }

    // ── Evento: añadir nota a registro ───────────────────────
    public void evaluarTrasAnadirNota(Usuario usuario) {
        logroService.otorgarSiNoTiene(usuario, "PRIMERA_NOTA");
    }
}
