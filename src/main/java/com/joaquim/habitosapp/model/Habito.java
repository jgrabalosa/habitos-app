package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "habito")
public class Habito {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "habito_id")
    private int habitoId;

    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", length = 500)
    private String descripcion;

    @Enumerated(EnumType.STRING)
    @Column(name = "frecuencia", nullable = false)
    private Frecuencia frecuencia;

    @Column(name = "meta")
    private int meta;

    @Column(name = "fecha_inicio", nullable = false)
    private LocalDate fechaInicio;

    @Column(name = "activo", nullable = false)
    private boolean activo;

    @ManyToOne
    @JoinColumn(name = "propietario_id", nullable = false, foreignKey = @ForeignKey(name = "FK_habito_usuario"))
    private Usuario propietario;

    @ManyToOne
    @JoinColumn(name = "tipo_id", foreignKey = @ForeignKey(name = "FK_habito_categoria"))
    private Categoria tipo;

    // Constructor vacío — obligatorio para JPA
    public Habito() {}

    // Constructor con parámetros
    public Habito(String nombre, String descripcion, Frecuencia frecuencia, int meta, Usuario propietario, Categoria tipo) {
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.frecuencia = frecuencia;
        this.meta = meta;
        this.propietario = propietario;
        this.tipo = tipo;
        this.fechaInicio = LocalDate.now();
        this.activo = true;
    }

    public int getHabitoId() { return habitoId; }
    public void setHabitoId(int habitoId) { this.habitoId = habitoId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public Frecuencia getFrecuencia() { return frecuencia; }
    public void setFrecuencia(Frecuencia frecuencia) { this.frecuencia = frecuencia; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

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