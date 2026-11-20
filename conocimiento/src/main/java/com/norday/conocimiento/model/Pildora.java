package com.norday.conocimiento.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;

/**
 * Unidad de microlearning. La vista previa (antes del match) muestra solo
 * `previewCorto` y la portada; el `contenidoCompleto` se destapa después.
 *
 * Desde V37 la píldora viene del repo de contenido: `codigo` es la clave del
 * importador, `previewCorto` es el gancho, las tarjetas viven en
 * `pildora_tarjeta` y `contenidoCompleto` las guarda unidas (biblioteca y
 * compartir). `libroOrigen` queda obsoleto: el importador lo rellena con
 * «Autor, Título» por compatibilidad. Los defaults son los de la migración.
 */
@Entity
@Table(name = "pildora",
        uniqueConstraints = @UniqueConstraint(name = "UK_pildora_codigo", columnNames = {"codigo"}),
        indexes = @Index(name = "idx_pildora_temario", columnList = "temario_id, orden_temario"))
public class Pildora {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "pildora_id")
    private int pildoraId;

    @Column(name = "titulo", nullable = false, length = 200)
    private String titulo;

    /** Cita o resumen muy breve para la vista previa, antes del match. */
    @Column(name = "preview_corto", nullable = false, length = 300)
    private String previewCorto;

    /**
     * Contenido completo en Markdown. Puede llevar imágenes embebidas con la
     * sintaxis `![alt](url)`, así que no tiene tope de longitud útil.
     */
    @Column(name = "contenido_completo", nullable = false, columnDefinition = "TEXT")
    private String contenidoCompleto;

    /** Referencia de texto libre al libro origen; no es una relación a una entidad. */
    @Column(name = "libro_origen", length = 200)
    private String libroOrigen;

    /** Portada del libro (Google Books) para la vista previa. */
    @Column(name = "imagen_url", length = 500)
    private String imagenUrl;

    @Column(name = "activa", nullable = false)
    private boolean activa = true;

    @Column(name = "fecha_creacion", nullable = false)
    private LocalDateTime fechaCreacion;

    /**
     * Clave estable del repo de contenido. Admite null solo porque la columna
     * se añadió a una tabla que ya existía (V37); toda píldora importada lo trae.
     */
    @Column(name = "codigo", length = 100)
    private String codigo;

    /** Null si es suelta: solo sale en el match, no en un temario. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "temario_id", foreignKey = @ForeignKey(name = "FK_pildora_temario"))
    private Temario temario;

    /** Su posición en el temario; null si es suelta. */
    @Column(name = "orden_temario")
    private Integer ordenTemario;

    @ColumnDefault("1")
    @Column(name = "nivel", nullable = false)
    private int nivel = 1;

    /**
     * Sube con cada cambio de texto en el repo. No es un {@code @Version} de
     * JPA: no controla escrituras concurrentes, solo dice qué texto hay.
     */
    @ColumnDefault("1")
    @Column(name = "version", nullable = false)
    private int version = 1;

    /** El libro que la amplía («Para saber más»); null si no hay. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "fuente_id", foreignKey = @ForeignKey(name = "FK_pildora_fuente"))
    private Fuente fuente;

    /** La licencia del texto (texto-propio, cc-by, cc0, dominio-publico). */
    @Column(name = "licencia", length = 30)
    private String licencia;

    // Constructor vacío — obligatorio para JPA
    public Pildora() {}

    // Constructor con parámetros
    public Pildora(String titulo, String previewCorto, String contenidoCompleto, String libroOrigen,
                   String imagenUrl, boolean activa, LocalDateTime fechaCreacion) {
        this.titulo = titulo;
        this.previewCorto = previewCorto;
        this.contenidoCompleto = contenidoCompleto;
        this.libroOrigen = libroOrigen;
        this.imagenUrl = imagenUrl;
        this.activa = activa;
        this.fechaCreacion = fechaCreacion;
    }

    public int getPildoraId() { return pildoraId; }
    public void setPildoraId(int pildoraId) { this.pildoraId = pildoraId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getPreviewCorto() { return previewCorto; }
    public void setPreviewCorto(String previewCorto) { this.previewCorto = previewCorto; }

    public String getContenidoCompleto() { return contenidoCompleto; }
    public void setContenidoCompleto(String contenidoCompleto) { this.contenidoCompleto = contenidoCompleto; }

    public String getLibroOrigen() { return libroOrigen; }
    public void setLibroOrigen(String libroOrigen) { this.libroOrigen = libroOrigen; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public boolean isActiva() { return activa; }
    public void setActiva(boolean activa) { this.activa = activa; }

    public LocalDateTime getFechaCreacion() { return fechaCreacion; }
    public void setFechaCreacion(LocalDateTime fechaCreacion) { this.fechaCreacion = fechaCreacion; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public Temario getTemario() { return temario; }
    public void setTemario(Temario temario) { this.temario = temario; }

    public Integer getOrdenTemario() { return ordenTemario; }
    public void setOrdenTemario(Integer ordenTemario) { this.ordenTemario = ordenTemario; }

    public int getNivel() { return nivel; }
    public void setNivel(int nivel) { this.nivel = nivel; }

    public int getVersion() { return version; }
    public void setVersion(int version) { this.version = version; }

    public Fuente getFuente() { return fuente; }
    public void setFuente(Fuente fuente) { this.fuente = fuente; }

    public String getLicencia() { return licencia; }
    public void setLicencia(String licencia) { this.licencia = licencia; }

    @Override
    public String toString() {
        return "Pildora{pildoraId=" + pildoraId + ", titulo='" + titulo + "', activa=" + activa + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Pildora pildora = (Pildora) o;
        return pildoraId == pildora.pildoraId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(pildoraId);
    }
}
