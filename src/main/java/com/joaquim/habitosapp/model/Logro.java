package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;

@Entity
@Table(name = "logro")
public class Logro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "logro_id")
    private int logroId;

    @Column(name = "codigo", nullable = false, unique = true, length = 50)
    private String codigo;

    @NotBlank(message = "El nombre es obligatorio")
    @Column(name = "nombre", nullable = false, length = 100)
    private String nombre;

    @Column(name = "descripcion", nullable = false, length = 255)
    private String descripcion;

    @Column(name = "categoria", nullable = false, length = 50)
    private String categoria;

    @Column(name = "nivel", nullable = false, length = 20)
    private String nivel;

    @Column(name = "puntos", nullable = false)
    private int puntos;

    @Column(name = "icono", length = 100)
    private String icono;

    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    // Constructor vacío — obligatorio para JPA
    public Logro() {}

    // Constructor con parámetros
    public Logro(String codigo, String nombre, String descripcion, String categoria,
                 String nivel, int puntos, String icono) {
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.nivel = nivel;
        this.puntos = puntos;
        this.icono = icono;
        this.activo = true;
    }

    public int getLogroId() { return logroId; }
    public void setLogroId(int logroId) { this.logroId = logroId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getCategoria() { return categoria; }
    public void setCategoria(String categoria) { this.categoria = categoria; }

    public String getNivel() { return nivel; }
    public void setNivel(String nivel) { this.nivel = nivel; }

    public int getPuntos() { return puntos; }
    public void setPuntos(int puntos) { this.puntos = puntos; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return "Logro{logroId=" + logroId + ", codigo='" + codigo + "', nombre='" + nombre + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Logro logro = (Logro) o;
        return logroId == logro.logroId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(logroId);
    }
}