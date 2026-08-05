package com.norday.habitos.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.ZoneId;

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

    /**
     * Inicio del periodo en el que se alcanzó la meta por última vez.
     *
     * Sustituye al antiguo booleano metaAlcanzadaPeriodoActual, que no sabía
     * a qué periodo pertenecía y dependía de que un cron lo limpiase a las
     * 00:05 de Madrid. Este sello se autocaduca: cuando cambia el periodo,
     * la comparación falla sola y nadie tiene que limpiar nada.
     */
    @Column(name = "periodo_meta_alcanzada")
    private LocalDate periodoMetaAlcanzada;

    @OneToOne
    @JoinColumn(name = "habito_ref", nullable = false, foreignKey = @ForeignKey(name = "FK_racha_habito"))
    private Habito habito;

    // Constructor vacío — obligatorio para JPA
    public Racha() {}

    // Constructor con parámetros
    public Racha(Habito habito, LocalDate hoy) {
        this.habito = habito;
        this.rachaActual = 0;
        this.rachaMaxima = 0;
        this.ultimaFecha = hoy;
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

    public LocalDate getPeriodoMetaAlcanzada() { return periodoMetaAlcanzada; }
    public void setPeriodoMetaAlcanzada(LocalDate periodoMetaAlcanzada) { this.periodoMetaAlcanzada = periodoMetaAlcanzada; }

    /**
     * ¿Ya se cumplió la meta en el periodo que corre ahora para este usuario?
     * Guardián para no subir la racha dos veces en el mismo periodo.
     */
    public boolean metaAlcanzadaEnPeriodoActual(ZoneId zona) {
        return periodoMetaAlcanzada != null
                && periodoMetaAlcanzada.equals(habito.getFrecuencia().rangoPeriodoActual(zona)[0]);
    }

    /**
     * ¿Sigue viva la racha? Lo está si la meta se cumplió en el periodo
     * actual o en el inmediatamente anterior. Si el último sello es más
     * antiguo, se saltó un periodo entero y la racha está rota.
     */
    public boolean sigueViva(ZoneId zona) {
        return periodoMetaAlcanzada != null
                && !periodoMetaAlcanzada.isBefore(habito.getFrecuencia().inicioPeriodoAnterior(zona));
    }

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