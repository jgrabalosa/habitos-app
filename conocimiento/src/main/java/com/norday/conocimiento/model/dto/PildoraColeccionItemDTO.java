package com.norday.conocimiento.model.dto;

public class PildoraColeccionItemDTO {

    private int pildoraId;
    private String titulo;
    private String imagenUrl;
    private String estado;
    private String categoriaPrincipal; // nombre, no código

    public int getPildoraId() { return pildoraId; }
    public void setPildoraId(int pildoraId) { this.pildoraId = pildoraId; }

    public String getTitulo() { return titulo; }
    public void setTitulo(String titulo) { this.titulo = titulo; }

    public String getImagenUrl() { return imagenUrl; }
    public void setImagenUrl(String imagenUrl) { this.imagenUrl = imagenUrl; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }

    public String getCategoriaPrincipal() { return categoriaPrincipal; }
    public void setCategoriaPrincipal(String categoriaPrincipal) { this.categoriaPrincipal = categoriaPrincipal; }
}
