package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "registro")
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registro_id")
    private int registroId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    @Column(name = "completado", nullable = false)
    private boolean completado;

    @Column(name = "nota", length = 500)
    private String nota;

    @ManyToOne
    @JoinColumn(name = "habito_ref", nullable = false, foreignKey = @ForeignKey(name = "FK_registro_habito"))
    private Habito habito;

    // Constructor vacío — obligatorio para JPA
    public Registro() {}

    // Constructor con parámetros
    public Registro(Habito habito, boolean completado, String nota) {
        this.habito = habito;
        this.completado = completado;
        this.nota = nota;
        this.fecha = LocalDate.now();
    }

    public int getRegistroId() { return registroId; }
    public void setRegistroId(int registroId) { this.registroId = registroId; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    @Override
    public String toString() {
        return "Registro{registroId=" + registroId + ", fecha=" + fecha + ", completado=" + completado + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Registro registro = (Registro) o;
        return registroId == registro.registroId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(registroId);
    }
}