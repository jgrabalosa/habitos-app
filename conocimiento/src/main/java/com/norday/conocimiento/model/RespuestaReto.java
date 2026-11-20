package com.norday.conocimiento.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Una respuesta a un reto (tabla respuesta_reto, V39): premio, racha,
 * idempotencia y, más adelante, análisis del aprendizaje.
 *
 * Como {@link LecturaPildora}: la clave la genera la app y un reintento con
 * ella no paga dos veces; {@code dia} es el día en la zona del usuario y
 * {@code respondidaEn}, el instante en UTC. {@code opcion} cuenta desde 0,
 * como {@link Reto#getIndiceCorrecta()}. {@code acierto} se guarda en vez de
 * recalcularse porque el reto puede cambiar después en el repo.
 */
@Entity
@Table(name = "respuesta_reto",
        uniqueConstraints = @UniqueConstraint(name = "UK_respuesta_reto_clave", columnNames = {"clave"}),
        indexes = @Index(name = "idx_respuesta_reto_usuario_dia", columnList = "usuario_id, dia"))
public class RespuestaReto {

    /** Lo que cabe en premio_logros. */
    public static final int LONGITUD_LOGROS = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_respuesta_reto_usuario"))
    private Usuario usuario;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reto_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_respuesta_reto_reto"))
    private Reto reto;

    @Column(name = "clave", nullable = false)
    private UUID clave;

    /** La opción elegida, desde 0. */
    @Column(name = "opcion", nullable = false)
    private int opcion;

    @Column(name = "acierto", nullable = false)
    private boolean acierto;

    /** El día en la zona del usuario. */
    @Column(name = "dia", nullable = false)
    private LocalDate dia;

    /** El instante, en UTC. */
    @Column(name = "respondida_en", nullable = false)
    private LocalDateTime respondidaEn;

    @ColumnDefault("0")
    @Column(name = "premio_monedas", nullable = false)
    private int premioMonedas = 0;

    @ColumnDefault("0")
    @Column(name = "premio_xp", nullable = false)
    private int premioXp = 0;

    /** Los códigos de los logros que desbloqueó, separados por comas; null si ninguno. */
    @Column(name = "premio_logros", length = LONGITUD_LOGROS)
    private String premioLogros;

    /** Si con esta respuesta el día llegó al tope de monedas. */
    @ColumnDefault("false")
    @Column(name = "premio_tope", nullable = false)
    private boolean premioTope = false;

    // Constructor vacío — obligatorio para JPA
    public RespuestaReto() {}

    public RespuestaReto(Usuario usuario, Reto reto, UUID clave, int opcion, boolean acierto,
                         LocalDate dia, LocalDateTime respondidaEn) {
        this.usuario = usuario;
        this.reto = reto;
        this.clave = clave;
        this.opcion = opcion;
        this.acierto = acierto;
        this.dia = dia;
        this.respondidaEn = respondidaEn;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public Reto getReto() { return reto; }
    public void setReto(Reto reto) { this.reto = reto; }

    public UUID getClave() { return clave; }
    public void setClave(UUID clave) { this.clave = clave; }

    public int getOpcion() { return opcion; }
    public void setOpcion(int opcion) { this.opcion = opcion; }

    public boolean isAcierto() { return acierto; }
    public void setAcierto(boolean acierto) { this.acierto = acierto; }

    public LocalDate getDia() { return dia; }
    public void setDia(LocalDate dia) { this.dia = dia; }

    public LocalDateTime getRespondidaEn() { return respondidaEn; }
    public void setRespondidaEn(LocalDateTime respondidaEn) { this.respondidaEn = respondidaEn; }

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
        return "RespuestaReto{id=" + id + ", acierto=" + acierto + ", dia=" + dia + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        RespuestaReto that = (RespuestaReto) o;
        return id == that.id;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(id);
    }
}
