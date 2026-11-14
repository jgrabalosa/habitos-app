package com.norday.habitos.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * Una versión del objetivo de un hábito: lo que pedía desde el día
 * {@code desde} hasta la víspera de la versión siguiente. Cada día se juzga
 * con la versión vigente en esa fecha —la de mayor {@code desde} que no la
 * pasa—, así que cambiar la meta o la frecuencia no reinterpreta lo que ya
 * se hizo.
 *
 * Puede haber como mucho una versión con {@code desde} futura: el cambio
 * programado. Si llega otro cambio antes de su fecha, la reemplaza.
 *
 * {@code minutosObjetivo} es null en los hábitos de veces.
 */
@Entity
@Table(name = "habito_objetivo",
        uniqueConstraints = @UniqueConstraint(name = "UK_habito_objetivo_habito_desde",
                columnNames = {"habito_ref", "desde"}))
public class HabitoObjetivo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habito_ref", nullable = false,
            foreignKey = @ForeignKey(name = "FK_habito_objetivo_habito"))
    private Habito habito;

    @Column(name = "desde", nullable = false)
    private LocalDate desde;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'VECES'")
    @Column(name = "unidad", nullable = false, length = 10)
    private Unidad unidad = Unidad.VECES;

    @Enumerated(EnumType.STRING)
    @Column(name = "frecuencia", nullable = false, length = 20)
    private Frecuencia frecuencia;

    @Column(name = "meta", nullable = false)
    private int meta;

    @Column(name = "minutos_objetivo")
    private Integer minutosObjetivo;

    // Mismo formato que Habito.diasSemana: días ISO separados por comas.
    @Column(name = "dias_semana", length = 20)
    private String diasSemana;

    // Cuándo se escribió la versión (UTC): es un sello de auditoría, no un
    // día de usuario. Lo rellena @PrePersist.
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    // Constructor vacío — obligatorio para JPA
    public HabitoObjetivo() {}

    public HabitoObjetivo(Habito habito, LocalDate desde, Objetivo objetivo) {
        this.habito = habito;
        this.desde = desde;
        aplicar(objetivo);
    }

    /** Pone en esta versión el objetivo dado. */
    public void aplicar(Objetivo objetivo) {
        this.unidad = objetivo.unidad();
        this.minutosObjetivo = objetivo.minutosObjetivo();
        this.frecuencia = objetivo.frecuencia();
        this.meta = objetivo.meta();
        this.diasSemana = objetivo.diasSemana();
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public LocalDate getDesde() { return desde; }
    public void setDesde(LocalDate desde) { this.desde = desde; }

    public Unidad getUnidad() { return unidad; }
    public void setUnidad(Unidad unidad) { this.unidad = unidad; }

    public Frecuencia getFrecuencia() { return frecuencia; }
    public void setFrecuencia(Frecuencia frecuencia) { this.frecuencia = frecuencia; }

    public int getMeta() { return meta; }
    public void setMeta(int meta) { this.meta = meta; }

    public Integer getMinutosObjetivo() { return minutosObjetivo; }
    public void setMinutosObjetivo(Integer minutosObjetivo) { this.minutosObjetivo = minutosObjetivo; }

    public String getDiasSemana() { return diasSemana; }
    public void setDiasSemana(String diasSemana) { this.diasSemana = diasSemana; }

    public LocalDateTime getCreadoEn() { return creadoEn; }

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    public String toString() {
        return "HabitoObjetivo{id=" + id + ", desde=" + desde + ", unidad=" + unidad
                + ", frecuencia=" + frecuencia + ", meta=" + meta
                + ", minutosObjetivo=" + minutosObjetivo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HabitoObjetivo that = (HabitoObjetivo) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
