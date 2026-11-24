package com.norday.conocimiento.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una lectura terminada de una píldora (tabla lectura_pildora, V39).
 *
 * La clave la genera la app y la identifica para siempre: un reintento con la
 * misma clave no crea otra fila ni paga dos veces, el patrón de
 * {@code sesion_enfoque}. {@code dia} es el día en la zona del usuario (racha
 * y tope diario); {@code leidaEn} es el instante, en UTC.
 *
 * Los {@code premio*} son lo que pagó esta lectura, ya recortado por el tope
 * del día, y se guardan para devolverlo igual en un reintento. Los defaults
 * son los de la migración, para que la tabla de los tests sea la misma.
 */
@Entity
@Table(name = "lectura_pildora",
        uniqueConstraints = @UniqueConstraint(name = "UK_lectura_pildora_clave", columnNames = {"clave"}),
        indexes = @Index(name = "idx_lectura_pildora_usuario_dia", columnList = "usuario_id, dia"))
public class LecturaPildora {

    /** Lo que cabe en premio_logros. */
    public static final int LONGITUD_LOGROS = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_lectura_pildora_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pildora_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_lectura_pildora_pildora"))
    private Pildora pildora;

    @Column(name = "clave", nullable = false)
    private UUID clave;

    /** El día en la zona del usuario. */
    @Column(name = "dia", nullable = false)
    private LocalDate dia;

    /** El instante, en UTC. */
    @Column(name = "leida_en", nullable = false)
    private LocalDateTime leidaEn;

    @ColumnDefault("0")
    @Column(name = "premio_monedas", nullable = false)
    private int premioMonedas = 0;

    @ColumnDefault("0")
    @Column(name = "premio_xp", nullable = false)
    private int premioXp = 0;

    /** Los códigos de los logros que desbloqueó, separados por comas; null si ninguno. */
    @Column(name = "premio_logros", length = LONGITUD_LOGROS)
    private String premioLogros;

    /** Si con esta lectura el día llegó al tope de monedas. */
    @ColumnDefault("false")
    @Column(name = "premio_tope", nullable = false)
    private boolean premioTope = false;

    // Constructor vacío — obligatorio para JPA
    public LecturaPildora() {}

    public LecturaPildora(Usuario usuario, Pildora pildora, UUID clave, LocalDate dia, LocalDateTime leidaEn) {
        this.usuario = usuario;
        this.pildora = pildora;
        this.clave = clave;
        this.dia = dia;
        this.leidaEn = leidaEn;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Pildora getPildora() { return pildora; }
    public void setPildora(Pildora pildora) { this.pildora = pildora; }

    public UUID getClave() { return clave; }
    public void setClave(UUID clave) { this.clave = clave; }

    public LocalDate getDia() { return dia; }
    public void setDia(LocalDate dia) { this.dia = dia; }

    public LocalDateTime getLeidaEn() { return leidaEn; }
    public void setLeidaEn(LocalDateTime leidaEn) { this.leidaEn = leidaEn; }

    public int getPremioMonedas() { return premioMonedas; }
    public void setPremioMonedas(int premioMonedas) { this.premioMonedas = premioMonedas; }

    public int getPremioXp() { return premioXp; }
    public void setPremioXp(int premioXp) { this.premioXp = premioXp; }

    public String getPremioLogros() { return premioLogros; }
    public void setPremioLogros(String premioLogros) { this.premioLogros = premioLogros; }

    public boolean isPremioTope() { return premioTope; }
    public void setPremioTope(boolean premioTope) { this.premioTope = premioTope; }

    @Override
    public String toString() {
        return "LecturaPildora{id=" + id + ", dia=" + dia + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LecturaPildora that = (LecturaPildora) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
