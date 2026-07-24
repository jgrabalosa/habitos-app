package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Frecuencia;
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
import java.util.Map;
import com.joaquim.habitosapp.model.dto.ResultadoExperienciaDTO;

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

    @Autowired
    private MascotaService mascotaService;

    // Un día de compromiso cumplido vale igual sea DIARIO o SEMANAL, meta 1 o meta 4:
    // el valor está en el compromiso diario, no en cómo esté configurado el hábito.
    private static final int PUNTOS_POR_DIA_COMPLETADO = 50;
    private static final int XP_POR_DIA_COMPLETADO = 5;

    public Map<String, Object> completarHabito(Habito habito, String nota) {
        // SEMANAL: máximo un completado por día (cada completado es un día distinto)
        if (habito.getFrecuencia() == Frecuencia.SEMANAL && registroDAO.existeRegistroHoy(habito)) {
            throw new RuntimeException("Este hábito ya se ha completado hoy");
        }

        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual();
        int completadosAntes = registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
        int meta = habito.getMeta();

        Registro registro = new Registro(habito, true, nota);
        registroDAO.save(registro);

        Usuario usuario = habito.getPropietario();
        int puntosGanados = 0;
        boolean subioNivel = false;
        int nivelNuevo = 0;

        // Puntos y XP solo en el instante exacto en que se alcanza la meta del día —
        // ni antes, ni de nuevo si sigues completando después de alcanzarla
        if (completadosAntes + 1 == meta) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, PUNTOS_POR_DIA_COMPLETADO, "HABITO_COMPLETADO",
                    habito.getHabitoId(), "Hábito completado: " + habito.getNombre()
            );
            puntosGanados += PUNTOS_POR_DIA_COMPLETADO;

            ResultadoExperienciaDTO resultadoXp =
                    mascotaService.ganarExperiencia(usuario.getUsuarioId(), XP_POR_DIA_COMPLETADO);
            subioNivel = resultadoXp.isSubioNivel();
            nivelNuevo = resultadoXp.getNivelNuevo();
        }

        boolean metaAlcanzadaAhora = actualizarRacha(habito, completadosAntes + 1, meta);
        if (metaAlcanzadaAhora) {
            puntosGanados += otorgarPuntosPorHitoRacha(usuario, habito);
        }

        List<String> logros = motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);

        // Cuándo mostrar el sheet de valoración: SEMANAL siempre (cada completado es
        // un día distinto), DIARIO solo en el último completado del día (al llegar a la meta)
        boolean mostrarValoracion = habito.getFrecuencia() == Frecuencia.SEMANAL
                || (completadosAntes + 1) >= meta;

        return Map.of(
                "logros", logros,
                "puntosGanados", puntosGanados,
                "registroId", registro.getRegistroId(),
                "mostrarValoracion", mostrarValoracion,
                "subioNivel", subioNivel,
                "nivelNuevo", nivelNuevo
        );
    }

    public int contarCompletadosPeriodoActual(Habito habito) {
        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual();
        return registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
    }

    /**
     * Actualiza la racha SOLO si se alcanza la meta del periodo por primera vez en ese periodo.
     * Devuelve true si la racha acaba de subir en esta llamada (para disparar puntos de hito).
     */
    private boolean actualizarRacha(Habito habito, int completadosEnPeriodo, int meta) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return false;

        if (racha.isMetaAlcanzadaPeriodoActual()) {
            return false; // ya subió este periodo, completar de más no hace nada
        }

        if (completadosEnPeriodo >= meta) {
            racha.setRachaActual(racha.getRachaActual() + 1);
            if (racha.getRachaActual() > racha.getRachaMaxima()) {
                racha.setRachaMaxima(racha.getRachaActual());
            }
            racha.setMetaAlcanzadaPeriodoActual(true);
            racha.setUltimaFecha(LocalDate.now());
            rachaDAO.update(racha);
            return true;
        }

        return false;
    }

    private int otorgarPuntosPorHitoRacha(Usuario usuario, Habito habito) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return 0;

        int actual = racha.getRachaActual();
        int puntos = switch (actual) {
            case 3 -> 50;
            case 7 -> 100;
            case 30 -> 300;
            case 100 -> 750;
            case 365 -> 2000;
            default -> 0;
        };

        if (puntos > 0) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, puntos, "HITO_RACHA", habito.getHabitoId(),
                    "Hito de racha (" + actual + ") en: " + habito.getNombre()
            );
        }
        return puntos;
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

    public void actualizarValoracion(int registroId, Integer valoracion) {
        if (valoracion == null || valoracion < 1 || valoracion > 5) {
            throw new IllegalArgumentException("La valoración debe estar entre 1 y 5");
        }
        Registro registro = registroDAO.findById(registroId);
        if (registro == null) {
            throw new RuntimeException("Registro no encontrado");
        }
        registro.setValoracion(valoracion);
        registroDAO.update(registro);
    }
}