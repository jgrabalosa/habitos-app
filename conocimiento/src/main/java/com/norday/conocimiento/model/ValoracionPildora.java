package com.norday.conocimiento.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Valoración de una píldora, tal como la recoge el widget `ValoracionSheet`
 * del paquete compartido: cinco estrellas más una nota personal.
 *
 * La nota es privada — solo la ve quien la escribió. Esto no es una reseña
 * pública y no debe exponerse como tal.
 */
@Entity
@Table(name = "valoracion_pildora")
public class ValoracionPildora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne
    @JoinColumn(name = "usuario_id", nullable = false, foreignKey = @ForeignKey(name = "FK_valoracion_pildora_usuario"))
    private Usuario usuario;

    @ManyToOne
    @JoinColumn(name = "pildora_id", nullable = false, foreignKey = @ForeignKey(name = "FK_valoracion_pildora_pildora"))
    private Pildora pildora;

    /** De 1 a 5. */
    @Column(name = "puntuacion", nullable = false)
    private int puntuacion;

    @Column(name = "nota_personal", length = 500)
    private String notaPersonal;

    @Column(name = "fecha", nullable = false)
    private LocalDateTime fecha;

    // Constructor vacío — obligatorio para JPA
    public ValoracionPildora() {}

    // Constructor con parámetros
    public ValoracionPildora(Usuario usuario, Pildora pildora, int puntuacion, String notaPersonal, LocalDateTime fecha) {
        this.usuario = usuario;
        this.pildora = pildora;
        this.puntuacion = puntuacion;
        this.notaPersonal = notaPersonal;
        this.fecha = fecha;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Pildora getPildora() { return pildora; }
    public void setPildora(Pildora pildora) { this.pildora = pildora; }

    public int getPuntuacion() { return puntuacion; }
    public void setPuntuacion(int puntuacion) { this.puntuacion = puntuacion; }

    public String getNotaPersonal() { return notaPersonal; }
    public void setNotaPersonal(String notaPersonal) { this.notaPersonal = notaPersonal; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    @Override
    public String toString() {
        return "ValoracionPildora{id=" + id + ", puntuacion=" + puntuacion + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ValoracionPildora that = (ValoracionPildora) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
