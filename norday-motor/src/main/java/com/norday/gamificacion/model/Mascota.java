package com.norday.gamificacion.model;

import com.norday.core.model.Usuario;
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

    /** Null mientras el usuario no le haya puesto nombre: es dato suyo. */
    @Column(name = "nombre")
    private String nombre;

    @Column(name = "fecha_ultima_comida")
    private LocalDate fechaUltimaComida;

    /**
     * Último día en que el usuario cumplió todo lo que tenía comprometido.
     * Null mientras no haya habido ninguno. De aquí sale el estado de ánimo.
     */
    @Column(name = "fecha_ultimo_dia_completo")
    private LocalDate fechaUltimoDiaCompleto;

    // Constructor vacío — obligatorio para JPA
    public Mascota() {}

    // Constructor con parámetros (fila creada perezosamente al primer GET)
    public Mascota(Usuario usuario) {
        this.usuario = usuario;
        this.experiencia = 0;
        // El nombre es dato del usuario: no debe nacer en un idioma concreto.
        // Mientras esté a null, el cliente muestra la fase localizada.
        this.nombre = null;
        this.fechaUltimaComida = null;
        this.fechaUltimoDiaCompleto = null;
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

    public LocalDate getFechaUltimoDiaCompleto() { return fechaUltimoDiaCompleto; }
    public void setFechaUltimoDiaCompleto(LocalDate fechaUltimoDiaCompleto) { this.fechaUltimoDiaCompleto = fechaUltimoDiaCompleto; }

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