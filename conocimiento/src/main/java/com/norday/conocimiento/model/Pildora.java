package com.norday.conocimiento.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Unidad de microlearning. La vista previa (antes del match) muestra solo
 * `previewCorto` y la portada; el `contenidoCompleto` se destapa después.
 */
@Entity
@Table(name = "pildora")
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
