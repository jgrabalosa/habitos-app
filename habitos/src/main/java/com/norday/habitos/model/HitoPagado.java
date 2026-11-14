package com.norday.habitos.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

/**
 * El libro de hitos: una fila por cada hito que un hábito ya ha cobrado.
 * Un hito se paga una sola vez por hábito, y esta tabla es quien lo
 * recuerda. Los días hechos se recalculan con la meta actual y pueden bajar
 * (al subir la meta, días pasados dejan de contar); sin el libro, al volver
 * a subir el contador el hito se pagaría otra vez.
 *
 * {@code registro} es el completado que lo pagó, para que deshacerlo borre
 * la fila. El relleno de V21 lo liga al registro que cerró el día hecho del
 * umbral. La columna admite null de todos modos.
 */
@Entity
@Table(name = "hito_pagado",
        uniqueConstraints = @UniqueConstraint(name = "UK_hito_pagado_habito_umbral",
                columnNames = {"habito_ref", "umbral"}))
public class HitoPagado {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habito_ref", nullable = false,
            foreignKey = @ForeignKey(name = "FK_hito_pagado_habito"))
    private Habito habito;

    /** Los días hechos del hito: uno de los umbrales de ParametrosPremio.HITOS. */
    @Column(name = "umbral", nullable = false)
    private int umbral;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "registro_ref",
            foreignKey = @ForeignKey(name = "FK_hito_pagado_registro"))
    private Registro registro;

    // Cuándo se pagó (UTC): es un sello de auditoría, no un día de usuario.
    // Lo rellena @PrePersist.
    @Column(name = "pagado_en", nullable = false, updatable = false)
    private LocalDateTime pagadoEn;

    // Constructor vacío — obligatorio para JPA
    public HitoPagado() {}

    public HitoPagado(Habito habito, int umbral, Registro registro) {
        this.habito = habito;
        this.umbral = umbral;
        this.registro = registro;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public int getUmbral() { return umbral; }
    public void setUmbral(int umbral) { this.umbral = umbral; }

    public Registro getRegistro() { return registro; }
    public void setRegistro(Registro registro) { this.registro = registro; }

    public LocalDateTime getPagadoEn() { return pagadoEn; }

    @PrePersist
    void alCrear() {
        if (pagadoEn == null) {
            pagadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    public String toString() {
        return "HitoPagado{id=" + id + ", umbral=" + umbral + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        HitoPagado that = (HitoPagado) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
