package com.norday.habitos.model.dto;

import com.norday.habitos.model.Frecuencia;
import com.norday.habitos.model.Habito;
import java.time.LocalTime;

/**
 * El cuerpo de POST /api/habitos y PUT /api/habitos/{id}.
 *
 * Solo lleva lo que decide el usuario. El id, el propietario, activo y
 * fechaInicio los fija el servidor. La categoria llega como id y la resuelve
 * HabitoService, que solo acepta las globales y las del propio usuario.
 */
public class HabitoEntradaDTO {

    private String nombre;
    private String descripcion;
    private Frecuencia frecuencia;
    private int meta;
    private String diasSemana;
    // Mismo valor por defecto que la entidad: si el cuerpo no lo trae, activado.
    private boolean recordatorioActivo = true;
    private LocalTime recordatorioHora;
    private Integer categoriaId;

    /** Un Habito sin id, sin propietario y sin categoria: los pone quien llama. */
    public Habito aHabito() {
        Habito habito = new Habito();
        habito.setNombre(nombre);
        habito.setDescripcion(descripcion);
        habito.setFrecuencia(frecuencia);
        habito.setMeta(meta);
        habito.setDiasSemana(diasSemana);
        habito.setRecordatorioActivo(recordatorioActivo);
        habito.setRecordatorioHora(recordatorioHora);
        return habito;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Frecuencia getFrecuencia() { return frecuencia; }
    public void setFrecuencia(Frecuencia frecuencia) { this.frecuencia = frecuencia; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

    public String getDiasSemana() { return diasSemana; }
    public void setDiasSemana(String diasSemana) { this.diasSemana = diasSemana; }

    public boolean isRecordatorioActivo() { return recordatorioActivo; }
    public void setRecordatorioActivo(boolean recordatorioActivo) { this.recordatorioActivo = recordatorioActivo; }

    public LocalTime getRecordatorioHora() { return recordatorioHora; }
    public void setRecordatorioHora(LocalTime recordatorioHora) { this.recordatorioHora = recordatorioHora; }

    public Integer getCategoriaId() { return categoriaId; }
    public void setCategoriaId(Integer categoriaId) { this.categoriaId = categoriaId; }
}
