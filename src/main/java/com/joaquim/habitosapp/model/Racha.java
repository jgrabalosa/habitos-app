package com.joaquim.habitosapp.model;

import jakarta.persistence.*;
import java.time.LocalDate;

@Entity
@Table(name = "racha")
public class Racha {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "racha_id")
    private int rachaId;

    @Column(name = "racha_actual", nullable = false)
    private int rachaActual;

    @Column(name = "racha_maxima", nullable = false)
    private int rachaMaxima;

    @Column(name = "ultima_fecha", nullable = false)
    private LocalDate ultimaFecha;

    @Column(name = "meta_alcanzada_periodo_actual", nullable = false)
    private boolean metaAlcanzadaPeriodoActual = false;

    @OneToOne
    @JoinColumn(name = "habito_ref", nullable = false, foreignKey = @ForeignKey(name = "FK_racha_habito"))
    private Habito habito;

    // Constructor vacío — obligatorio para JPA
    public Racha() {}

    // Constructor con parámetros
    public Racha(Habito habito) {
        this.habito = habito;
        this.rachaActual = 0;
        this.rachaMaxima = 0;
        this.ultimaFecha = LocalDate.now();
    }

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

    public boolean isMetaAlcanzadaPeriodoActual() { return metaAlcanzadaPeriodoActual; }
    public void setMetaAlcanzadaPeriodoActual(boolean metaAlcanzadaPeriodoActual) { this.metaAlcanzadaPeriodoActual = metaAlcanzadaPeriodoActual; }

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