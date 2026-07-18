package com.joaquim.habitosapp.model.dto;

import com.joaquim.habitosapp.model.Habito;
import java.util.List;

public class DashboardHabitoDTO {

    private Habito habito;
    private boolean completadoHoy;
    private int completadosPeriodo;
    private List<String> fechasCompletadas; // fechas ISO (yyyy-MM-dd) para el mini-heatmap

    public DashboardHabitoDTO(Habito habito, boolean completadoHoy, int completadosPeriodo,
                              List<String> fechasCompletadas) {
        this.habito = habito;
        this.completadoHoy = completadoHoy;
        this.completadosPeriodo = completadosPeriodo;
        this.fechasCompletadas = fechasCompletadas;
    }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public boolean isCompletadoHoy() { return completadoHoy; }
    public void setCompletadoHoy(boolean completadoHoy) { this.completadoHoy = completadoHoy; }

    public int getCompletadosPeriodo() { return completadosPeriodo; }
    public void setCompletadosPeriodo(int completadosPeriodo) { this.completadosPeriodo = completadosPeriodo; }

    public List<String> getFechasCompletadas() { return fechasCompletadas; }
    public void setFechasCompletadas(List<String> fechasCompletadas) { this.fechasCompletadas = fechasCompletadas; }
}