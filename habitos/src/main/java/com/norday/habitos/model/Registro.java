package com.norday.habitos.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

@Entity
@Table(name = "registro",
        uniqueConstraints = @UniqueConstraint(name = "UK_registro_clave_cliente",
                columnNames = {"clave_cliente"}))
public class Registro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "registro_id")
    private int registroId;

    @Column(name = "fecha", nullable = false)
    private LocalDate fecha;

    // Cuándo se marcó el registro (UTC). `fecha` es el día al que corresponde,
    // no el momento en que se marcó. Lo rellena @PrePersist.
    @Column(name = "creado_en", nullable = false, updatable = false)
    private LocalDateTime creadoEn;

    @Column(name = "completado", nullable = false)
    private boolean completado;

    @Column(name = "nota", length = 500)
    private String nota;

    @Column(name = "valoracion")
    private Integer valoracion; // 1-5, null si el usuario no valoró

    // Lo que aporta el registro a su día: los minutos en un hábito de
    // minutos, 1 en uno de veces. Los "hechos" de un día son la suma.
    @ColumnDefault("1")
    @Column(name = "cantidad", nullable = false)
    private int cantidad = 1;

    @Enumerated(EnumType.STRING)
    @ColumnDefault("'MANUAL'")
    @Column(name = "origen", nullable = false, length = 20)
    private OrigenRegistro origen = OrigenRegistro.MANUAL;

    // La clave que manda el cliente con cada completado para que un
    // reintento no cree otro registro (ver RegistroService). Única en toda la
    // tabla. No sale en el JSON: es un detalle del protocolo, no un dato.
    @JsonIgnore
    @Column(name = "clave_cliente")
    private UUID claveCliente;

    // No sale en el JSON. Se serializaba en un solo sitio, GET
    // /api/registros/habito/{id}, que devuelve la lista de registros en crudo:
    // allí el cliente ya sabe de qué hábito son —los ha pedido por su id— y el
    // hábito entero venía repetido en cada elemento. Además era LAZY, así que
    // serializarlo dependía de que open-in-view siguiera activo.
    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "habito_ref", nullable = false, foreignKey = @ForeignKey(name = "FK_registro_habito"))
    private Habito habito;

    // Constructor vacío — obligatorio para JPA
    public Registro() {}

    // Constructor con parámetros. La fecha la aporta quien crea el registro:
    // es el "hoy" del usuario en su zona, no el del servidor.
    public Registro(Habito habito, boolean completado, String nota, LocalDate fecha) {
        this.habito = habito;
        this.completado = completado;
        this.nota = nota;
        this.fecha = fecha;
    }

    public int getRegistroId() { return registroId; }
    public void setRegistroId(int registroId) { this.registroId = registroId; }

    public LocalDate getFecha() { return fecha; }
    public void setFecha(LocalDate fecha) { this.fecha = fecha; }

    public boolean isCompletado() { return completado; }
    public void setCompletado(boolean completado) { this.completado = completado; }

    public String getNota() { return nota; }
    public void setNota(String nota) { this.nota = nota; }

    public Integer getValoracion() { return valoracion; }
    public void setValoracion(Integer valoracion) { this.valoracion = valoracion; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public OrigenRegistro getOrigen() { return origen; }
    public void setOrigen(OrigenRegistro origen) { this.origen = origen; }

    public UUID getClaveCliente() { return claveCliente; }
    public void setClaveCliente(UUID claveCliente) { this.claveCliente = claveCliente; }

    public Habito getHabito() { return habito; }
    public void setHabito(Habito habito) { this.habito = habito; }

    public LocalDateTime getCreadoEn() { return creadoEn; }

    @PrePersist
    void alCrear() {
        if (creadoEn == null) {
            creadoEn = LocalDateTime.now(ZoneOffset.UTC);
        }
    }

    @Override
    public String toString() {
        return "Registro{registroId=" + registroId + ", fecha=" + fecha + ", completado=" + completado + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Registro registro = (Registro) o;
        return registroId == registro.registroId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(registroId);
    }
}