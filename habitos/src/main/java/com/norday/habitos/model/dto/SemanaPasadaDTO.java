package com.norday.habitos.model.dto;

import com.norday.habitos.model.Tendencia;

import java.util.List;

/**
 * «Así fue tu semana»: el resumen de la semana anterior a la de hoy, de lunes
 * a domingo, en la zona del usuario. Solo lectura.
 *
 * {@code desde} y {@code hasta} son el lunes y el domingo (ISO).
 * {@code hayActividad} es si hubo algún registro esa semana.
 * {@code hechos} y {@code total} suman, de los siete días, lo hecho y lo que
 * tocaba en la sección del día (como el avance de Hoy);
 * {@code diasCompletos} son los días en que se completó esa sección.
 * {@code semanales} son los semanales de esa semana, con lo hecho y su meta.
 * {@code ritmoNivel} y {@code ritmoTendencia} son los de hoy, como en Hoy;
 * null si no hay hábitos activos. {@code masSube} es el hábito cuyo ritmo más
 * subió esa semana; null si ninguno subió de nivel.
 */
public record SemanaPasadaDTO(String desde, String hasta, boolean hayActividad,
                              int hechos, int total, int diasCompletos,
                              List<SemanalSemanaPasadaDTO> semanales,
                              Integer ritmoNivel, Tendencia ritmoTendencia,
                              MasSubeDTO masSube) {

    /** Un semanal de esa semana: lo hecho, su meta y si la cumplió. */
    public record SemanalSemanaPasadaDTO(int habitoId, String nombre, String icono,
                                         int hechos, int meta, boolean completo) {}

    /** El hábito cuyo ritmo más subió: su nivel (1 a 5) al empezar la semana y al acabarla. */
    public record MasSubeDTO(int habitoId, String nombre, int nivelAntes, int nivelAhora) {}
}
