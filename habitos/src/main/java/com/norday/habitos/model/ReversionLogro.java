package com.norday.habitos.model;

import jakarta.persistence.*;

/**
 * Un logro desbloqueado por un completado concreto, para poder retirarlo si
 * el completado se deshace. Guardamos el id del logro, no el código: es más
 * estable.
 */
@Entity
@Table(name = "reversion_logro")
public class ReversionLogro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reversion_ref", nullable = false, foreignKey = @ForeignKey(name = "FK_reversion_logro"))
    private ReversionRegistro reversion;

    @Column(name = "logro_ref", nullable = false)
    private int logroRef;

    // Constructor vacío — obligatorio para JPA
    public ReversionLogro() {}

    // Constructor con parámetros
    public ReversionLogro(ReversionRegistro reversion, int logroRef) {
        this.reversion = reversion;
        this.logroRef = logroRef;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public ReversionRegistro getReversion() { return reversion; }
    public void setReversion(ReversionRegistro reversion) { this.reversion = reversion; }

    public int getLogroRef() { return logroRef; }
    public void setLogroRef(int logroRef) { this.logroRef = logroRef; }

    @Override
    public String toString() {
        return "ReversionLogro{id=" + id + ", logroRef=" + logroRef + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReversionLogro that = (ReversionLogro) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
