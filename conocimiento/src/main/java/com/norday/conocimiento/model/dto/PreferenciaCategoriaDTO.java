package com.norday.conocimiento.model.dto;

/**
 * Viaja en los dos sentidos. Al devolverlo va completo; al recibirlo en el PUT
 * solo se miran {@code categoriaId} y {@code estado} — el resto lo ignora el
 * servicio aunque el cliente lo mande de vuelta tal cual lo recibió.
 */
public class PreferenciaCategoriaDTO {

    private int categoriaId;
    private String nombre;
    private String icono;
    private String color;
    private String estado;

    public int getCategoriaId() { return categoriaId; }
    public void setCategoriaId(int categoriaId) { this.categoriaId = categoriaId; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getEstado() { return estado; }
    public void setEstado(String estado) { this.estado = estado; }
}
