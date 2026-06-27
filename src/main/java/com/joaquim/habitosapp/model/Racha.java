package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "racha")
public class Racha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private int rachaId;

    @Column(nullable = false)
    private int rachaActual;

    @Column(nullable = false)
    private int rachaMaxima;

    @Column(nullable = false)
    private LocalDate ultimaFecha;

    @OneToOne
    @JoinColumn(name = "habito_ref", nullable = false)
    private Habito habito;

    // Getters y Setters
    public int getRachaId() { return rachaId; }
    public void setRachaId(int rachaId) { this.rachaId = rachaId; }

    public int getRachaActual() { return rachaActual; }
    public void setRachaActual(int rachaActual) { this.rachaActual = rachaActual; }

    public int getRachaMaxima() { return rachaMaxima; }
    public void setRachaMaxima(int rachaMaxima) { this.rachaMaxima = rachaMaxima; }

    public LocalDate getUltimaFecha() { return ultimaFecha; }
    public void setUltimaFecha(LocalDate ultimaFecha) { this.ultimaFecha = ultimaFecha; }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    @Override
    public String toString() {
        return "Racha{rachaId=" + rachaId + ", rachaActual=" + rachaActual + ", rachaMaxima=" + rachaMaxima + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Racha racha = (Racha) o;
        return rachaId == racha.rachaId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(rachaId);
    }
}