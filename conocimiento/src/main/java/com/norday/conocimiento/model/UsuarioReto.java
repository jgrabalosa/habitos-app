package com.norday.conocimiento.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDateTime;

/**
 * El estado del repaso espaciado (FSRS) de un reto para un usuario (tabla
 * usuario_reto, V39). Una fila por par usuario-reto; se crea al leer la
 * píldora. El reto está en la bandeja cuando {@code venceEn} ya ha pasado.
 *
 * {@code repasos == 0} quiere decir que todavía no se ha respondido nunca:
 * entonces estabilidad y dificultad valen 0 y no significan nada (FSRS no
 * tiene estado antes del primer repaso, y después la estabilidad siempre es
 * mayor que 0). Las fechas van en UTC.
 *
 * estabilidad y dificultad son double y DOUBLE PRECISION en la migración, no
 * NUMERIC (la lección de V5).
 */
@Entity
@Table(name = "usuario_reto",
        uniqueConstraints = @UniqueConstraint(name = "UK_usuario_reto", columnNames = {"usuario_id", "reto_id"}),
        indexes = @Index(name = "idx_usuario_reto_vence", columnList = "usuario_id, vence_en"))
public class UsuarioReto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuario_reto_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reto_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_usuario_reto_reto"))
    private Reto reto;

    @ColumnDefault("0")
    @Column(name = "estabilidad", nullable = false)
    private double estabilidad = 0;

    @ColumnDefault("0")
    @Column(name = "dificultad", nullable = false)
    private double dificultad = 0;

    /** Cuántas veces se ha respondido. */
    @ColumnDefault("0")
    @Column(name = "repasos", nullable = false)
    private int repasos = 0;

    /** Cuántas de ellas fueron un fallo. */
    @ColumnDefault("0")
    @Column(name = "fallos", nullable = false)
    private int fallos = 0;

    /** La última respuesta; null si no se ha respondido nunca. */
    @Column(name = "ultimo_repaso")
    private LocalDateTime ultimoRepaso;

    /** Cuándo vuelve a la bandeja. */
    @Column(name = "vence_en", nullable = false)
    private LocalDateTime venceEn;

    // Constructor vacío — obligatorio para JPA
    public UsuarioReto() {}

    /** Un reto recién leído: sin repasos, vence cuando toque. */
    public UsuarioReto(Usuario usuario, Reto reto, LocalDateTime venceEn) {
        this.usuario = usuario;
        this.reto = reto;
        this.venceEn = venceEn;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Reto getReto() { return reto; }
    public void setReto(Reto reto) { this.reto = reto; }

    public double getEstabilidad() { return estabilidad; }
    public void setEstabilidad(double estabilidad) { this.estabilidad = estabilidad; }

    public double getDificultad() { return dificultad; }
    public void setDificultad(double dificultad) { this.dificultad = dificultad; }

    public int getRepasos() { return repasos; }
    public void setRepasos(int repasos) { this.repasos = repasos; }

    public int getFallos() { return fallos; }
    public void setFallos(int fallos) { this.fallos = fallos; }

    public LocalDateTime getUltimoRepaso() { return ultimoRepaso; }
    public void setUltimoRepaso(LocalDateTime ultimoRepaso) { this.ultimoRepaso = ultimoRepaso; }

    public LocalDateTime getVenceEn() { return venceEn; }
    public void setVenceEn(LocalDateTime venceEn) { this.venceEn = venceEn; }

    @Override
    public String toString() {
        return "UsuarioReto{id=" + id + ", repasos=" + repasos + ", venceEn=" + venceEn + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        UsuarioReto that = (UsuarioReto) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
