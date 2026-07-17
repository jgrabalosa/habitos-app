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
import java.time.DayOfWeek;
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

    private static final int PUNTOS_HABITO_COMPLETADO = 100; // provisional

    public Map<String, Object> completarHabito(Habito habito, String nota) {
        LocalDate[] periodo = calcularRangoPeriodoActual(habito);
        int completadosAntes = registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
        int meta = habito.getMeta();

        Registro registro = new Registro(habito, true, nota);
        registroDAO.save(registro);

        Usuario usuario = habito.getPropietario();
        int puntosGanados = 0;

        // Puntos solo hasta alcanzar la meta del periodo — evita farmeo con clics extra
        if (completadosAntes < meta) {
            usuarioMonedaService.registrarMovimiento(
                    usuario, PUNTOS_HABITO_COMPLETADO, "HABITO_COMPLETADO",
                    habito.getHabitoId(), "Hábito completado: " + habito.getNombre()
            );
            puntosGanados += PUNTOS_HABITO_COMPLETADO;
        }

        boolean metaAlcanzadaAhora = actualizarRacha(habito, completadosAntes + 1, meta);
        if (metaAlcanzadaAhora) {
            puntosGanados += otorgarPuntosPorHitoRacha(usuario, habito);
        }

        List<String> logros = motorLogrosService.evaluarTrasCompletarRegistro(usuario, habito);

        return Map.of("logros", logros, "puntosGanados", puntosGanados);
    }

    /**
     * Calcula el rango de fechas [desde, hasta] del periodo actual del hábito,
     * según su frecuencia. DIARIO = solo hoy. SEMANAL = semana natural actual (lunes a domingo).
     */
    private LocalDate[] calcularRangoPeriodoActual(Habito habito) {
        LocalDate hoy = LocalDate.now();
        if (habito.getFrecuencia() == Frecuencia.SEMANAL) {
            LocalDate lunes = hoy.with(DayOfWeek.MONDAY);
            LocalDate domingo = lunes.plusDays(6);
            return new LocalDate[]{lunes, domingo};
        }
        return new LocalDate[]{hoy, hoy};
    }

    public int contarCompletadosPeriodoActual(Habito habito) {
        LocalDate[] periodo = calcularRangoPeriodoActual(habito);
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
}