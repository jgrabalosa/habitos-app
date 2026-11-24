package com.norday.conocimiento.model;

import jakarta.persistence.*;

/**
 * Un libro, artículo o texto de dominio público (tabla fuente, V37): el
 * «Para saber más» de una píldora y las fuentes citables.
 *
 * La procedencia completa, afirmación por afirmación, se queda en el repo de
 * contenido; aquí solo lo que la app enseña. El importador la actualiza por
 * {@code codigo}.
 */
@Entity
@Table(name = "fuente",
        uniqueConstraints = @UniqueConstraint(name = "UK_fuente_codigo", columnNames = {"codigo"}))
public class Fuente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fuente_id")
    private int fuenteId;

    @Column(name = "codigo", nullable = false, length = 100)
    private String codigo;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 20)
    private TipoFuente tipo;

    @Column(name = "titulo", nullable = false, length = 300)
    private String titulo;

    @Column(name = "autor", length = 200)
    private String autor;

    /** Año de publicación; null si no se sabe. */
    @Column(name = "anio")
    private Integer anio;

    @Column(name = "url", length = 500)
    private String url;

    /** La licencia de la fuente, tal como la declara el repo. */
    @Column(name = "licencia", length = 30)
    private String licencia;

    // Constructor vacío — obligatorio para JPA
    public Fuente() {}

    public Fuente(String codigo, TipoFuente tipo, String titulo, String autor) {
        this.codigo = codigo;
        this.tipo = tipo;
        this.titulo = titulo;
        this.autor = autor;
    }

    public int getFuenteId() { return fuenteId; }
    public void setFuenteId(int fuenteId) { this.fuenteId = fuenteId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public TipoFuente getTipo() { return tipo; }
    public void setTipo(TipoFuente tipo) { this.tipo = tipo; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getAutor() { return autor; }
    public void setAutor(String autor) { this.autor = autor; }

    public Integer getAnio() { return anio; }
    public void setAnio(Integer anio) { this.anio = anio; }

    public String getUrl() { return url; }
    public void setUrl(String url) { this.url = url; }

    public String getLicencia() { return licencia; }
    public void setLicencia(String licencia) { this.licencia = licencia; }

    @Override
    public String toString() {
        return "Fuente{fuenteId=" + fuenteId + ", codigo='" + codigo + "', tipo=" + tipo + "}";
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Fuente fuente = (Fuente) o;
        return fuenteId == fuente.fuenteId;
    }

    @Override
    public int hashCode() {
        return Integer.hashCode(fuenteId);
    }
}
