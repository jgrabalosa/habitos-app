package com.norday.habitos.model.dto;

import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Unidad;
import java.time.LocalDate;
import java.time.LocalTime;

/**
 * El habito tal y como sale hacia el cliente en /{id}, /dashboard,
 * /resumen y /semana.
 *
 * La categoria viaja aplanada en tres campos, los tres null si el habito no
 * tiene. `propietario` no esta: el cliente ya sabe de quien son sus habitos.
 *
 * `unidad` y `minutosObjetivo` (null en VECES) son el objetivo de minutos,
 * como `frecuencia` y `meta`. La app ya instalada no los lee.
 *
 * `icono` es el código que dibuja la app (null si no tiene); no es del objetivo.
 */
public class HabitoDTO {

    private final int habitoId;
    private final String nombre;
    private final String descripcion;
    private final String icono;
    private final Frecuencia frecuencia;
    private final int meta;
    private final Unidad unidad;
    private final Integer minutosObjetivo;
    private final String diasSemana;
    private final LocalDate fechaInicio;
    private final boolean recordatorioActivo;
    private final LocalTime recordatorioHora;
    private final boolean activo;
    private final Integer categoriaId;
    private final String categoriaCodigo;
    private final String categoriaNombre;

    private HabitoDTO(Habito habito) {
        this.habitoId = habito.getHabitoId();
        this.nombre = habito.getNombre();
        this.descripcion = habito.getDescripcion();
        this.icono = habito.getIcono();
        this.frecuencia = habito.getFrecuencia();
        this.meta = habito.getMeta();
        this.unidad = habito.getUnidad();
        this.minutosObjetivo = habito.getMinutosObjetivo();
        this.diasSemana = habito.getDiasSemana();
        this.fechaInicio = habito.getFechaInicio();
        this.recordatorioActivo = habito.isRecordatorioActivo();
        this.recordatorioHora = habito.getRecordatorioHora();
        this.activo = habito.isActivo();
        Categoria tipo = habito.getTipo();
        this.categoriaId = tipo == null ? null : tipo.getCategoriaId();
        this.categoriaCodigo = tipo == null ? null : tipo.getCodigo();
        this.categoriaNombre = tipo == null ? null : tipo.getNombre();
    }

    public static HabitoDTO desde(Habito habito) {
        return habito == null ? null : new HabitoDTO(habito);
    }

    public int getHabitoId() { return habitoId; }
    public String getNombre() { return nombre; }
    public String getDescripcion() { return descripcion; }
    public String getIcono() { return icono; }
    public Frecuencia getFrecuencia() { return frecuencia; }
    public int getMeta() { return meta; }
    public Unidad getUnidad() { return unidad; }
    public Integer getMinutosObjetivo() { return minutosObjetivo; }
    public String getDiasSemana() { return diasSemana; }
    public LocalDate getFechaInicio() { return fechaInicio; }
    public boolean isRecordatorioActivo() { return recordatorioActivo; }
    public LocalTime getRecordatorioHora() { return recordatorioHora; }
    public boolean isActivo() { return activo; }
    public Integer getCategoriaId() { return categoriaId; }
    public String getCategoriaCodigo() { return categoriaCodigo; }
    public String getCategoriaNombre() { return categoriaNombre; }
}
