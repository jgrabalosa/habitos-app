package com.norday.conocimiento.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

import java.util.ArrayList;
import java.util.List;

/**
 * Una pregunta de opción múltiple sobre una píldora (tabla reto, V38). Llega a
 * la bandeja del usuario un tiempo después de leer y vuelve según el repaso
 * espaciado (ver {@link UsuarioReto}).
 *
 * Las opciones viven en reto_opcion, en orden: {@code indiceCorrecta} cuenta
 * desde 0 sobre esa lista. El importador lo actualiza por {@code codigo} y lo
 * retira con {@code activo = false}, nunca lo borra: los repasos y las
 * respuestas de los usuarios siguen apuntando aquí.
 */
@Entity
@Table(name = "reto",
        uniqueConstraints = @UniqueConstraint(name = "UK_reto_codigo", columnNames = {"codigo"}),
        indexes = @Index(name = "idx_reto_pildora", columnList = "pildora_id"))
public class Reto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reto_id")
    private int retoId;

    @Column(name = "codigo", nullable = false, length = 100)
    private String codigo;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pildora_id", nullable = false,
            foreignKey = @ForeignKey(name = "FK_reto_pildora"))
    private Pildora pildora;

    @Column(name = "enunciado", nullable = false, length = 500)
    private String enunciado;

    /**
     * Las opciones, en el orden en que se enseñan (de 2 a 4). La columna
     * {@code orden} de reto_opcion es la posición en la lista, desde 0.
     */
    @ElementCollection
    @CollectionTable(name = "reto_opcion",
            joinColumns = @JoinColumn(name = "reto_id"),
            foreignKey = @ForeignKey(name = "FK_reto_opcion_reto"))
    @OrderColumn(name = "orden")
    @Column(name = "texto", nullable = false, length = 300)
    private List<String> opciones = new ArrayList<>();

    /** Posición de la opción correcta en {@link #opciones}, desde 0. */
    @Column(name = "indice_correcta", nullable = false)
    private int indiceCorrecta;

    /** Lo que se enseña después de responder, se acierte o no. */
    @Column(name = "explicacion", length = 1000)
    private String explicacion;

    @ColumnDefault("true")
    @Column(name = "activo", nullable = false)
    private boolean activo = true;

    // Constructor vacío — obligatorio para JPA
    public Reto() {}

    public Reto(String codigo, Pildora pildora, String enunciado, List<String> opciones,
                int indiceCorrecta, String explicacion) {
        this.codigo = codigo;
        this.pildora = pildora;
        this.enunciado = enunciado;
        this.opciones = new ArrayList<>(opciones);
        this.indiceCorrecta = indiceCorrecta;
        this.explicacion = explicacion;
    }

    /** ¿Es la correcta la opción elegida? Fuera de rango no lo es. */
    public boolean esCorrecta(int opcion) {
        return opcion == indiceCorrecta;
    }

    public int getRetoId() { return retoId; }
    public void setRetoId(int retoId) { this.retoId = retoId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public Pildora getPildora() { return pildora; }
    public void setPildora(Pildora pildora) { this.pildora = pildora; }

    public String getEnunciado() { return enunciado; }
    public void setEnunciado(String enunciado) { this.enunciado = enunciado; }

    public List<String> getOpciones() { return opciones; }
    public void setOpciones(List<String> opciones) { this.opciones = opciones; }

    public int getIndiceCorrecta() { return indiceCorrecta; }
    public void setIndiceCorrecta(int indiceCorrecta) { this.indiceCorrecta = indiceCorrecta; }

    public String getExplicacion() { return explicacion; }
    public void setExplicacion(String explicacion) { this.explicacion = explicacion; }

    public boolean isActivo() { return activo; }
    public void setActivo(boolean activo) { this.activo = activo; }

    @Override
    public String toString() {
        return "Reto{retoId=" + retoId + ", codigo='" + codigo + "', activo=" + activo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Reto reto = (Reto) o;
        return retoId == reto.retoId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(retoId);
    }
}
