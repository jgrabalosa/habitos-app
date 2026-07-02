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
    public void evaluarTrasCrearHabito(Usuario usuario) {
        List<Habito> activos = habitoDAO.findActivos(usuario);

        if (activos.size() == 1) {
            otorgar(usuario, "PRIMER_HABITO");
        }
        if (activos.size() == 3) {
            otorgar(usuario, "HABITOS_ACTIVOS_3");
        }
        if (activos.size() == 5) {
            otorgar(usuario, "HABITOS_ACTIVOS_5");
        }

        comprobarCategoriasUsadas(usuario, activos);
        comprobarFrecuenciasMixtas(usuario, activos);
    }

    private void comprobarCategoriasUsadas(Usuario usuario, List<Habito> activos) {
        Set<Integer> categoriasDistintas = new HashSet<>();
        for (Habito h : activos) {
            if (h.getTipo() != null) {
                categoriasDistintas.add(h.getTipo().getCategoriaId());
            }
        }

        if (categoriasDistintas.size() == 3) {
            otorgar(usuario, "CATEGORIAS_3");
        }
        if (categoriasDistintas.size() == 5) {
            otorgar(usuario, "CATEGORIAS_5");
        }
    }

    private void comprobarFrecuenciasMixtas(Usuario usuario, List<Habito> activos) {
        boolean tieneDiario = false, tieneSemanal = false, tieneMensual = false;
        for (Habito h : activos) {
            switch (h.getFrecuencia()) {
                case DIARIO -> tieneDiario = true;
                case SEMANAL -> tieneSemanal = true;
                case MENSUAL -> tieneMensual = true;
                case PERSONALIZADO -> {} // no cuenta para este logro
            }
        }

        if (tieneDiario && tieneSemanal && tieneMensual) {
            otorgar(usuario, "FRECUENCIAS_MIXTAS");
        }
    }

    // ── Evento: crear categoría personalizada ────────────────
    public void evaluarTrasCrearCategoria(Usuario usuario, List<Categoria> categoriasCreadorUsuario) {
        if (categoriasCreadorUsuario.size() == 1) {
            otorgar(usuario, "PRIMERA_CATEGORIA");
        }
        if (categoriasCreadorUsuario.size() == 3) {
            otorgar(usuario, "CATEGORIAS_PERSONALIZADAS_3");
        }
    }

    // ── Evento: editar hábito ────────────────────────────────
    public void evaluarTrasEditarHabito(Usuario usuario) {
        otorgar(usuario, "EDITAR_HABITO");
    }

    // ── Evento: ver detalle de hábito ────────────────────────
    public void evaluarTrasVerDetalleHabito(Usuario usuario) {
        otorgar(usuario, "VER_DETALLE_HABITO");
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

        int totalConNota = registroDAO.contarConNotaPorUsuario(usuario.getUsuarioId());
        if (totalConNota == 10) {
            otorgar(usuario, "NOTAS_10");
        }
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