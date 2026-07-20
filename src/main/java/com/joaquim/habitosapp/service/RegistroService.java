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

    // Recompensa por completar, según frecuencia (provisional: se afinará en el
    // reequilibrio, donde esta misma tabla ganará la columna de XP de mascota)
    private static int puntosPorCompletar(Frecuencia frecuencia) {
        return switch (frecuencia) {
            case DIARIO -> 100;
            case SEMANAL -> 150;
        };
    }

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

        // Puntos solo hasta alcanzar la meta del periodo — evita farmeo con clics extra
        if (completadosAntes < meta) {
            int puntosBase = puntosPorCompletar(habito.getFrecuencia());
            usuarioMonedaService.registrarMovimiento(
                    usuario, puntosBase, "HABITO_COMPLETADO",
                    habito.getHabitoId(), "Hábito completado: " + habito.getNombre()
            );
            puntosGanados += puntosBase;
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
                "mostrarValoracion", mostrarValoracion
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