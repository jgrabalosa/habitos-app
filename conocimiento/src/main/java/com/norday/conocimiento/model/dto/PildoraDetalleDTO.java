package com.norday.conocimiento.model.dto;

import java.util.List;

public class PildoraDetalleDTO {

    private int pildoraId;
    private String titulo;
    private String contenidoCompleto;
    private String libroOrigen;
    private String imagenUrl;
    private List<String> categorias; // nombres, no códigos
    private String estado;           // VISTA o GUARDADA
    private Integer valoracionUsuario; // null si aún no ha valorado

    public int getPildoraId() { return pildoraId; }
    public void setPildoraId(int pildoraId) { this.pildoraId = pildoraId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getContenidoCompleto() { return contenidoCompleto; }
    public void setContenidoCompleto(String contenidoCompleto) { this.contenidoCompleto = contenidoCompleto; }

    public String getLibroOrigen() { return libroOrigen; }
    public void setLibroOrigen(String libroOrigen) { this.libroOrigen = libroOrigen; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public List<String> getCategorias() { return categorias; }
    public void setCategorias(List<String> categorias) { this.categorias = categorias; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public Integer getValoracionUsuario() { return valoracionUsuario; }
    public void setValoracionUsuario(Integer valoracionUsuario) { this.valoracionUsuario = valoracionUsuario; }
}
