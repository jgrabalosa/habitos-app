package com.norday.gamificacion.model.dto;

import java.time.LocalDate;

/**
 * El ánimo de la mascota un día concreto, el de la zona del usuario. El
 * código es el mismo que el de {@code MascotaDTO.animo}: triste, dormida,
 * feliz o exultante.
 */
public record AnimoDiaDTO(LocalDate fecha, String animo) {
}
