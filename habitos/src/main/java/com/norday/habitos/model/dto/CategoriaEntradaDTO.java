package com.norday.habitos.model.dto;

/**
 * El cuerpo de POST /api/categorias y PUT /api/categorias/{id}.
 *
 * Solo lleva lo que decide el usuario: {@code nombre} (obligatorio, recortado,
 * de 2 a 30 caracteres) e {@code icono} (opcional: null, o de 1 a 16
 * caracteres; es un emoji y puede ocupar varios). Lo demás que llegue en el
 * cuerpo (código, color, creador, esGlobal...) se ignora. Lo valida
 * CategoriaService: 400 si no cumple.
 *
 * En el PUT, un {@code icono} null quita el icono: esta entrada es nueva y
 * la app ya instalada no edita categorías, así que no hay cliente viejo al
 * que proteger con «null = sin cambio».
 */
public class CategoriaEntradaDTO {

    private String nombre;
    private String icono;

    public CategoriaEntradaDTO() {}

    public CategoriaEntradaDTO(String nombre, String icono) {
        this.nombre = nombre;
        this.icono = icono;
    }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getIcono() { return icono; }
    public void setIcono(String icono) { this.icono = icono; }
}
