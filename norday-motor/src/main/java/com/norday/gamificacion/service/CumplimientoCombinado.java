package com.norday.gamificacion.service;

import com.norday.gamificacion.model.AvanceDeHoy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * El cumplimiento de hoy de todas las apps a la vez, para el ánimo de la
 * mascota (16-nov-2026). Spring recolecta las implementaciones de
 * CumplimientoDiarioPort, una por app, igual que los DestinoMinutos.
 *
 * Solo cuentan las apps en uso ({@link CumplimientoDiarioPort#enUso}); si no
 * hay ninguna, todas. Sobre ellas:
 * <ul>
 *   <li>COMPLETO si todas lo están: «exultante» es todo lo de hoy hecho;</li>
 *   <li>EMPEZADO si alguna ha empezado o está completa;</li>
 *   <li>AUSENTE si todas lo están;</li>
 *   <li>SIN_EMPEZAR en lo demás.</li>
 * </ul>
 * Con una sola app, la respuesta es la suya, sin cambiar nada: quien solo usa
 * una app ve a la mascota igual que antes.
 *
 * No implementa el puerto a propósito: si lo hiciera, se recolectaría a sí
 * misma.
 */
@Service
public class CumplimientoCombinado {

    @Autowired
    private List<CumplimientoDiarioPort> cumplimientos;

    public AvanceDeHoy comoVaHoy(int usuarioId) {
        return combinar(cumplimientosQueCuentan(usuarioId).stream().map(c -> c.comoVaHoy(usuarioId)).toList());
    }

    /** Si todo lo de hoy está hecho en todas las apps que cuentan: la misma regla que COMPLETO. */
    public boolean hoyCumplido(int usuarioId) {
        List<CumplimientoDiarioPort> cuentan = cumplimientosQueCuentan(usuarioId);
        return !cuentan.isEmpty() && cuentan.stream().allMatch(c -> c.hoyCumplido(usuarioId));
    }

    private List<CumplimientoDiarioPort> cumplimientosQueCuentan(int usuarioId) {
        List<CumplimientoDiarioPort> enUso = new ArrayList<>();
        for (CumplimientoDiarioPort cumplimiento : cumplimientos) {
            if (cumplimiento.enUso(usuarioId)) enUso.add(cumplimiento);
        }
        return enUso.isEmpty() ? cumplimientos : enUso;
    }

    /** La regla de arriba, sin red ni base de datos. Con una lista vacía, SIN_EMPEZAR. */
    public static AvanceDeHoy combinar(List<AvanceDeHoy> avances) {
        if (avances.isEmpty()) return AvanceDeHoy.SIN_EMPEZAR;
        if (avances.stream().allMatch(a -> a == AvanceDeHoy.COMPLETO)) return AvanceDeHoy.COMPLETO;
        if (avances.stream().anyMatch(a -> a == AvanceDeHoy.EMPEZADO || a == AvanceDeHoy.COMPLETO)) {
            return AvanceDeHoy.EMPEZADO;
        }
        if (avances.stream().allMatch(a -> a == AvanceDeHoy.AUSENTE)) return AvanceDeHoy.AUSENTE;
        return AvanceDeHoy.SIN_EMPEZAR;
    }
}
