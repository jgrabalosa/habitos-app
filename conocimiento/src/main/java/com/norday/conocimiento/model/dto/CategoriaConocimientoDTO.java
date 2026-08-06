package com.norday.conocimiento.model.dto;

public class CategoriaConocimientoDTO {

    private int categoriaId;
    private String codigo;
    private String nombre;
    private String descripcion;
    private String color;
    private String icono;
    private int orden;

    public int getCategoriaId() { return categoriaId; }
    public void setCategoriaId(int categoriaId) { this.categoriaId = categoriaId; }

    public String getCodigo() { return codigo; }
    public void setCodigo(String codigo) { this.codigo = codigo; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getDescripcion() { return descripcion; }
    public void setDescripcion(String descripcion) { this.descripcion = descripcion; }

    public String getColor() { return color; }
    public void setColor(String color) { this.color = color; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }

    public int getOrden() { return orden; }
    public void setOrden(int orden) { this.orden = orden; }
}
