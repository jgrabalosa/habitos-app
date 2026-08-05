package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.dto.ResultadoExperienciaDTO;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

@Service
public class RegistroService {

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private LogrosHabitosService logrosHabitosService;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

    @Autowired
    private RachaService rachaService;

    @Autowired
    private HabitoService habitoService;

    // Un día de compromiso cumplido vale igual sea DIARIO o SEMANAL, meta 1 o meta 4:
    // el valor está en el compromiso diario, no en cómo esté configurado el hábito.
    private static final int PUNTOS_POR_DIA_COMPLETADO = 50;
    private static final int XP_POR_DIA_COMPLETADO = 5;

    public Map<String, Object> completarHabito(Habito habito, String nota) {
        ZoneId zona = rachaService.zonaDe(habito);
        LocalDate hoy = LocalDate.now(zona);

        // SEMANAL: máximo un completado por día (cada completado es un día distinto)
        if (habito.getFrecuencia() == Frecuencia.SEMANAL
                && registroDAO.existeRegistroEnFecha(habito, hoy)) {
            throw new RuntimeException("Este hábito ya se ha completado hoy");
        }

        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(zona);
        int completadosAntes = registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
        int meta = habito.getMeta();

        Registro registro = new Registro(habito, true, nota, hoy);
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

        boolean metaAlcanzadaAhora = actualizarRacha(habito, completadosAntes + 1, meta, zona, hoy);
        if (metaAlcanzadaAhora) {
            puntosGanados += otorgarPuntosPorHitoRacha(usuario, habito);
        }

        // Con este registro puede haberse cerrado el día entero. Va después de
        // guardar el Registro a propósito: la consulta de esDiaCompleto es JPQL,
        // así que Hibernate hace flush antes y el registro recién creado cuenta.
        if (habitoService.esDiaCompleto(usuario)) {
            mascotaService.registrarDiaCompleto(usuario.getUsuarioId());
        }

        // Los logros de racha leen rachaActual/rachaMaxima en crudo, pero aquí
        // ya es seguro: actualizarRacha acaba de normalizarla en esta misma
        // llamada, así que no puede haber valores rancios.
        List<String> logros = logrosHabitosService.evaluarTrasCompletarRegistro(usuario, habito);

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
        LocalDate[] periodo = habito.getFrecuencia().rangoPeriodoActual(rachaService.zonaDe(habito));
        return registroDAO.findByHabitoAndRango(habito, periodo[0], periodo[1]).size();
    }

    /**
     * Actualiza la racha SOLO si se alcanza la meta del periodo por primera vez en ese periodo.
     * Devuelve true si la racha acaba de subir en esta llamada (para disparar puntos de hito).
     */
    private boolean actualizarRacha(Habito habito, int completadosEnPeriodo, int meta,
                                    ZoneId zona, LocalDate hoy) {
        Racha racha = rachaDAO.findByHabito(habito);
        if (racha == null) return false;

        if (racha.metaAlcanzadaEnPeriodoActual(zona)) {
            return false; // ya subió este periodo, completar de más no hace nada
        }

        if (completadosEnPeriodo >= meta) {
            // Rotura perezosa: si se saltó un periodo entero, la racha no
            // continúa desde el valor viejo — vuelve a empezar en 1.
            int base = racha.sigueViva(zona) ? racha.getRachaActual() : 0;
            racha.setRachaActual(base + 1);
            if (racha.getRachaActual() > racha.getRachaMaxima()) {
                racha.setRachaMaxima(racha.getRachaActual());
            }
            racha.setPeriodoMetaAlcanzada(habito.getFrecuencia().rangoPeriodoActual(zona)[0]);
            racha.setUltimaFecha(hoy);
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
        return registroDAO.existeRegistroEnFecha(habito, LocalDate.now(rachaService.zonaDe(habito)));
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
        logrosHabitosService.evaluarTrasAnadirNota(usuario);
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