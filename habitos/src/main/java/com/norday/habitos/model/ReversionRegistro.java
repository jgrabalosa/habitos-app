package com.norday.habitos.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Instantánea del estado previo a completar un hábito, para poder revertirlo
 * con exactitud. Los campos _previa/_previo son nullable a propósito: si el
 * hábito no tiene racha o el usuario no tiene mascota, no hay nada que
 * restaurar y null lo dice mejor que un cero.
 */
@Entity
@Table(name = "reversion_registro")
public class ReversionRegistro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reversion_id")
    private int reversionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_ref", nullable = false, foreignKey = @ForeignKey(name = "FK_reversion_registro"))
    private Registro registro;

    @Column(name = "racha_actual_previa")
    private Integer rachaActualPrevia;

    @Column(name = "racha_maxima_previa")
    private Integer rachaMaximaPrevia;

    @Column(name = "periodo_meta_alcanzada_previo")
    private LocalDate periodoMetaAlcanzadaPrevio;

    @Column(name = "ultima_fecha_previa")
    private LocalDate ultimaFechaPrevia;

    @Column(name = "mascota_experiencia_previa")
    private Integer mascotaExperienciaPrevia;

    @Column(name = "mascota_dia_completo_previo")
    private LocalDate mascotaDiaCompletoPrevio;

    @Column(name = "monedas_otorgadas", nullable = false)
    private int monedasOtorgadas;

    @OneToMany(mappedBy = "reversion", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    private List<ReversionLogro> logros = new ArrayList<>();

    // Constructor vacío — obligatorio para JPA
    public ReversionRegistro() {}

    // Constructor con parámetros
    public ReversionRegistro(Registro registro, Integer rachaActualPrevia, Integer rachaMaximaPrevia,
                              LocalDate periodoMetaAlcanzadaPrevio, LocalDate ultimaFechaPrevia,
                              Integer mascotaExperienciaPrevia, LocalDate mascotaDiaCompletoPrevio,
                              int monedasOtorgadas) {
        this.registro = registro;
        this.rachaActualPrevia = rachaActualPrevia;
        this.rachaMaximaPrevia = rachaMaximaPrevia;
        this.periodoMetaAlcanzadaPrevio = periodoMetaAlcanzadaPrevio;
        this.ultimaFechaPrevia = ultimaFechaPrevia;
        this.mascotaExperienciaPrevia = mascotaExperienciaPrevia;
        this.mascotaDiaCompletoPrevio = mascotaDiaCompletoPrevio;
        this.monedasOtorgadas = monedasOtorgadas;
    }

    public int getReversionId() { return reversionId; }
    public void setReversionId(int reversionId) { this.reversionId = reversionId; }

    public Registro getRegistro() { return registro; }
    public void setRegistro(Registro registro) { this.registro = registro; }

    public Integer getRachaActualPrevia() { return rachaActualPrevia; }
    public void setRachaActualPrevia(Integer rachaActualPrevia) { this.rachaActualPrevia = rachaActualPrevia; }

    public Integer getRachaMaximaPrevia() { return rachaMaximaPrevia; }
    public void setRachaMaximaPrevia(Integer rachaMaximaPrevia) { this.rachaMaximaPrevia = rachaMaximaPrevia; }

    public LocalDate getPeriodoMetaAlcanzadaPrevio() { return periodoMetaAlcanzadaPrevio; }
    public void setPeriodoMetaAlcanzadaPrevio(LocalDate periodoMetaAlcanzadaPrevio) { this.periodoMetaAlcanzadaPrevio = periodoMetaAlcanzadaPrevio; }

    public LocalDate getUltimaFechaPrevia() { return ultimaFechaPrevia; }
    public void setUltimaFechaPrevia(LocalDate ultimaFechaPrevia) { this.ultimaFechaPrevia = ultimaFechaPrevia; }

    public Integer getMascotaExperienciaPrevia() { return mascotaExperienciaPrevia; }
    public void setMascotaExperienciaPrevia(Integer mascotaExperienciaPrevia) { this.mascotaExperienciaPrevia = mascotaExperienciaPrevia; }

    public LocalDate getMascotaDiaCompletoPrevio() { return mascotaDiaCompletoPrevio; }
    public void setMascotaDiaCompletoPrevio(LocalDate mascotaDiaCompletoPrevio) { this.mascotaDiaCompletoPrevio = mascotaDiaCompletoPrevio; }

    public int getMonedasOtorgadas() { return monedasOtorgadas; }
    public void setMonedasOtorgadas(int monedasOtorgadas) { this.monedasOtorgadas = monedasOtorgadas; }

    public List<ReversionLogro> getLogros() { return logros; }
    public void setLogros(List<ReversionLogro> logros) { this.logros = logros; }

    @Override
    public String toString() {
        return "ReversionRegistro{reversionId=" + reversionId + ", monedasOtorgadas=" + monedasOtorgadas + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        ReversionRegistro that = (ReversionRegistro) o;
        return reversionId == that.reversionId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(reversionId);
    }
}
