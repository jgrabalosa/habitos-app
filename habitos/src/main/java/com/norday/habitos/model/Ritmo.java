package com.norday.habitos.model;

import jakarta.persistence.*;
import java.time.LocalDate;

/**
 * El ritmo de un hábito, guardado como un ancla: {@code valor} es el ritmo
 * (de 0 a 1) al empezar el día {@code desde}. El ritmo de hoy no se guarda:
 * se calcula al leer, repasando los registros desde {@code desde}.
 *
 * Por eso completar o deshacer un hábito no escribe aquí. Solo se mueve el
 * ancla, hacia delante, cuando lo anterior a ella ya no puede cambiar.
 */
@Entity
@Table(name = "ritmo")
public class Ritmo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ritmo_id")
    private int ritmoId;

    @Column(name = "valor", nullable = false)
    private double valor;

    @Column(name = "desde", nullable = false)
    private LocalDate desde;

    /**
     * El ancla anterior: el ritmo al empezar el día {@code desdePrevio}. Se
     * guarda cada vez que el ancla avanza, para poder calcular el ritmo de
     * hace siete días. Null mientras el ancla no haya avanzado nunca.
     */
    @Column(name = "valor_previo")
    private Double valorPrevio;

    @Column(name = "desde_previo")
    private LocalDate desdePrevio;

    /**
     * El día del último cambio de frecuencia u objetivo del hábito. El
     * periodo que lo contiene solo puede subir el ritmo. Null si el hábito
     * no ha cambiado nunca.
     */
    @Column(name = "cambio")
    private LocalDate cambio;

    @OneToOne
    @JoinColumn(name = "habito_ref", nullable = false, unique = true,
            foreignKey = @ForeignKey(name = "FK_ritmo_habito"))
    private Habito habito;

    // Constructor vacío — obligatorio para JPA
    public Ritmo() {}

    /** Ritmo de un hábito que empieza: valor de partida, desde {@code desde}. */
    public Ritmo(Habito habito, LocalDate desde) {
        this.habito = habito;
        this.valor = ParametrosRitmo.VALOR_INICIAL;
        this.desde = desde;
    }

    public int getRitmoId() { return ritmoId; }
    public void setRitmoId(int ritmoId) { this.ritmoId = ritmoId; }

    public double getValor() { return valor; }
    public void setValor(double valor) { this.valor = valor; }

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }

    public Double getValorPrevio() { return valorPrevio; }
    public void setValorPrevio(Double valorPrevio) { this.valorPrevio = valorPrevio; }

    public LocalDate getDesdePrevio() { return desdePrevio; }
    public void setDesdePrevio(LocalDate desdePrevio) { this.desdePrevio = desdePrevio; }

    public LocalDate getCambio() { return cambio; }
    public void setCambio(LocalDate cambio) { this.cambio = cambio; }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    @Override
    public String toString() {
        return "Ritmo{ritmoId=" + ritmoId + ", valor=" + valor + ", desde=" + desde + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Ritmo ritmo = (Ritmo) o;
        return ritmoId == ritmo.ritmoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(ritmoId);
    }
}
