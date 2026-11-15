package com.norday.enfoque.model;

import com.norday.core.model.Usuario;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

/**
 * Una sesión de concentración de Norday Focus (tabla sesion_enfoque, V26).
 *
 * La clave la pone la app al empezar la sesión y la identifica para siempre:
 * un reintento con la misma clave no crea otra fila. La misma clave viaja al
 * destino, así que tampoco suma dos veces allí.
 *
 * {@code dia} es el día de inicio en la zona del usuario, y es el día al que
 * van los minutos. {@code destinoRef} es opaco: un id del dominio del destino,
 * sin clave ajena, porque este módulo no conoce sus tablas.
 *
 * Los defaults son los de la migración, para que la tabla que genera
 * Hibernate en los tests sea la misma.
 */
@Entity
@Table(name = "sesion_enfoque",
        uniqueConstraints = @UniqueConstraint(name = "UK_sesion_enfoque_clave", columnNames = {"clave"}),
        indexes = {
                @Index(name = "idx_sesion_enfoque_usuario_dia", columnList = "usuario_ref, dia"),
                @Index(name = "idx_sesion_enfoque_usuario_inicio", columnList = "usuario_ref, inicio_utc")
        })
public class SesionEnfoque {

    /** Lo que cabe en logros_aportados. */
    public static final int LONGITUD_LOGROS = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private int id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_ref", nullable = false,
            foreignKey = @ForeignKey(name = "FK_sesion_enfoque_usuario"))
    private Usuario usuario;

    @Column(name = "clave", nullable = false)
    private UUID clave;

    /** Instante de inicio, en UTC. */
    @Column(name = "inicio_utc", nullable = false)
    private LocalDateTime inicioUtc;

    @Column(name = "duracion_seg", nullable = false)
    private int duracionSeg;

    @Column(name = "duracion_objetivo_min", nullable = false)
    private int duracionObjetivoMin;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 12)
    private EstadoSesion estado;

    @Column(name = "dia", nullable = false)
    private LocalDate dia;

    @Column(name = "destino_tipo", length = 20)
    private String destinoTipo;

    @Column(name = "destino_ref")
    private Long destinoRef;

    @Enumerated(EnumType.STRING)
    @Column(name = "aporte_estado", nullable = false, length = 12)
    private EstadoAporte aporteEstado;

    @Column(name = "aporte_motivo", length = 40)
    private String aporteMotivo;

    @ColumnDefault("0")
    @Column(name = "minutos_aportados", nullable = false)
    private int minutosAportados = 0;

    /** Lo que pagó el destino al llevar los minutos (0 si nada). */
    @ColumnDefault("0")
    @Column(name = "monedas_aportadas", nullable = false)
    private int monedasAportadas = 0;

    @ColumnDefault("0")
    @Column(name = "xp_aportada", nullable = false)
    private int xpAportada = 0;

    /** Los códigos de los logros que desbloqueó el aporte, separados por comas; null si ninguno. */
    @Column(name = "logros_aportados", length = LONGITUD_LOGROS)
    private String logrosAportados;

    /** Sello de auditoría, en UTC. */
    @Column(name = "creado_en", nullable = false)
    private LocalDateTime creadoEn;

    // Constructor vacío — obligatorio para JPA
    public SesionEnfoque() {}

    /**
     * Los minutos de la sesión: los minutos enteros transcurridos si cuenta,
     * 0 si es PERDIDA.
     */
    public int minutos() {
        return estado != null && estado.cuenta() ? duracionSeg / 60 : 0;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public Usuario getUsuario() { return usuario; }
    public void setUsuario(Usuario usuario) { this.usuario = usuario; }

    public UUID getClave() { return clave; }
    public void setClave(UUID clave) { this.clave = clave; }

    public LocalDateTime getInicioUtc() { return inicioUtc; }
    public void setInicioUtc(LocalDateTime inicioUtc) { this.inicioUtc = inicioUtc; }

    public int getDuracionSeg() { return duracionSeg; }
    public void setDuracionSeg(int duracionSeg) { this.duracionSeg = duracionSeg; }

    public int getDuracionObjetivoMin() { return duracionObjetivoMin; }
    public void setDuracionObjetivoMin(int duracionObjetivoMin) { this.duracionObjetivoMin = duracionObjetivoMin; }

    public EstadoSesion getEstado() { return estado; }
    public void setEstado(EstadoSesion estado) { this.estado = estado; }

    public LocalDate getDia() { return dia; }
    public void setDia(LocalDate dia) { this.dia = dia; }

    public String getDestinoTipo() { return destinoTipo; }
    public void setDestinoTipo(String destinoTipo) { this.destinoTipo = destinoTipo; }

    public Long getDestinoRef() { return destinoRef; }
    public void setDestinoRef(Long destinoRef) { this.destinoRef = destinoRef; }

    public EstadoAporte getAporteEstado() { return aporteEstado; }
    public void setAporteEstado(EstadoAporte aporteEstado) { this.aporteEstado = aporteEstado; }

    public String getAporteMotivo() { return aporteMotivo; }
    public void setAporteMotivo(String aporteMotivo) { this.aporteMotivo = aporteMotivo; }

    public int getMinutosAportados() { return minutosAportados; }
    public void setMinutosAportados(int minutosAportados) { this.minutosAportados = minutosAportados; }

    public int getMonedasAportadas() { return monedasAportadas; }
    public void setMonedasAportadas(int monedasAportadas) { this.monedasAportadas = monedasAportadas; }

    public int getXpAportada() { return xpAportada; }
    public void setXpAportada(int xpAportada) { this.xpAportada = xpAportada; }

    public String getLogrosAportados() { return logrosAportados; }
    public void setLogrosAportados(String logrosAportados) { this.logrosAportados = logrosAportados; }

    /** Los logros aportados como lista (vacía si ninguno). */
    public List<String> logros() {
        return logrosAportados == null || logrosAportados.isBlank() ? List.of()
                : Arrays.stream(logrosAportados.split(",")).filter(c -> !c.isBlank()).toList();
    }

    /**
     * Los códigos en el formato de logros_aportados: separados por comas, null
     * si no hay ninguno. Si no caben todos (no debería pasar: son unos pocos
     * códigos cortos), se guardan los que caben enteros.
     */
    public static String textoDeLogros(List<String> codigos) {
        if (codigos == null || codigos.isEmpty()) return null;
        List<String> caben = new ArrayList<>();
        int longitud = 0;
        for (String codigo : codigos) {
            int con = longitud + (caben.isEmpty() ? 0 : 1) + codigo.length();
            if (con > LONGITUD_LOGROS) break;
            caben.add(codigo);
            longitud = con;
        }
        return caben.isEmpty() ? null : String.join(",", caben);
    }

    public LocalDateTime getCreadoEn() { return creadoEn; }
    public void setCreadoEn(LocalDateTime creadoEn) { this.creadoEn = creadoEn; }
}
