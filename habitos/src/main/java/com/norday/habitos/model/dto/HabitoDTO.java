package com.norday.habitos.model.dto;

import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * El habito tal y como viaja dentro del JSON de /dashboard, /resumen y
 * /semana.
 *
 * Reproduce los mismos nombres y los mismos tipos que hoy serializa la
 * entidad Habito en esos endpoints, para que el JSON salga identico.
 * `propietario` no esta porque la entidad lo lleva @JsonIgnore. `tipo`
 * sigue siendo la entidad Categoria: aplanarla es un paso posterior.
 */
public class HabitoDTO {

    private final int habitoId;
    private final String nombre;
    private final String descripcion;
    private final Frecuencia frecuencia;
    private final int meta;
    private final String diasSemana;
    private final LocalDate fechaInicio;
    private final boolean recordatorioActivo;
    private final LocalTime recordatorioHora;
    private final boolean activo;
    private final Categoria tipo;

    private HabitoDTO(Habito habito) {
        this.habitoId = habito.getHabitoId();
        this.nombre = habito.getNombre();
        this.descripcion = habito.getDescripcion();
        this.frecuencia = habito.getFrecuencia();
        this.meta = habito.getMeta();
        this.diasSemana = habito.getDiasSemana();
        this.fechaInicio = habito.getFechaInicio();
        this.recordatorioActivo = habito.isRecordatorioActivo();
        this.recordatorioHora = habito.getRecordatorioHora();
        this.activo = habito.isActivo();
        this.tipo = habito.getTipo();
    }

    public static HabitoDTO desde(Habito habito) {
        return habito == null ? null : new HabitoDTO(habito);
    }

    public int getHabitoId() { return habitoId; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public Frecuencia getFrecuencia() { return frecuencia; }
    public int getMeta() { return meta; }
    public String getDiasSemana() { return diasSemana; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public boolean isRecordatorioActivo() { return recordatorioActivo; }
    public LocalTime getRecordatorioHora() { return recordatorioHora; }
    public boolean isActivo() { return activo; }
    public Categoria getTipo() { return tipo; }
}
