package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "habito")
public class Habito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int habitoId;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frecuencia frecuencia;

    @Column(nullable = false)
    private LocalDate fechaInicio;

    @Column(nullable = false)
    private boolean activo;

    @ManyToOne
    @JoinColumn(name = "propietario_id", nullable = false)
    private Usuario propietario;

    @ManyToOne
    @JoinColumn(name = "tipo_id")
    private Categoria tipo;

    // Getters y Setters
    public int getHabitoId() { return habitoId; }
    public void setHabitoId(int habitoId) { this.habitoId = habitoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Frecuencia getFrecuencia() { return frecuencia; }
    public void setFrecuencia(Frecuencia frecuencia) { this.frecuencia = frecuencia; }

    public LocalDate getFechaInicio() { return fechaInicio; }
    public void setFechaInicio(LocalDate fechaInicio) { this.fechaInicio = fechaInicio; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    public Usuario getPropietario() { return propietario; }
    public void setPropietario(Usuario propietario) { this.propietario = propietario; }

    public Categoria getTipo() { return tipo; }
    public void setTipo(Categoria tipo) { this.tipo = tipo; }

    @Override
    public String toString() {
        return "Habito{habitoId=" + habitoId + ", nombre='" + nombre + "', activo=" + activo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Habito habito = (Habito) o;
        return habitoId == habito.habitoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(habitoId);
    }
}