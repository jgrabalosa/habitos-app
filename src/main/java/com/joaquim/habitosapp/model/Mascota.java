package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "mascota")
public class Mascota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "mascota_id")
    private int mascotaId;

    @OneToOne
    @JoinColumn(name = "usuario_id", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "FK_mascota_usuario"))
    private Usuario usuario;

    @Column(name = "experiencia", nullable = false)
    private int experiencia;

    @Column(name = "nombre", nullable = false)
    private String nombre;

    @Column(name = "fecha_ultima_comida")
    private LocalDate fechaUltimaComida;

    // Constructor vacío — obligatorio para JPA
    public Mascota() {}

    // Constructor con parámetros (fila creada perezosamente al primer GET)
    public Mascota(Usuario usuario) {
        this.usuario = usuario;
        this.experiencia = 0;
        this.nombre = "Huevo";
        this.fechaUltimaComida = null;
    }

    public int getMascotaId() { return mascotaId; }
    public void setMascotaId(int mascotaId) { this.mascotaId = mascotaId; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public int getExperiencia() { return experiencia; }
    public void setExperiencia(int experiencia) { this.experiencia = experiencia; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public LocalDate getFechaUltimaComida() { return fechaUltimaComida; }
    public void setFechaUltimaComida(LocalDate fechaUltimaComida) { this.fechaUltimaComida = fechaUltimaComida; }

    @Override
    public String toString() {
        return "Mascota{mascotaId=" + mascotaId + ", experiencia=" + experiencia + ", nombre='" + nombre + "'}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Mascota mascota = (Mascota) o;
        return mascotaId == mascota.mascotaId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(mascotaId);
    }
}