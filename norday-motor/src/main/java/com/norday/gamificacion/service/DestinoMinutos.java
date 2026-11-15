package com.norday.gamificacion.service;

import com.norday.gamificacion.model.DestinoDisponible;
import com.norday.gamificacion.model.ProgresoDestino;
import com.norday.gamificacion.model.ResultadoAporte;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Un sitio al que una app puede llevar minutos que el usuario ha dedicado a
 * algo: una sesión de concentración, por ejemplo. El motor no sabe qué es el
 * destino ni cómo paga: cada dominio aporta su implementación y quien lleva
 * los minutos recibe todas como {@code List<DestinoMinutos>} y elige por
 * {@link #tipo()}. Mismo patrón que {@link CumplimientoDiarioPort}.
 *
 * Vive en gamificacion porque lo que devuelve habla su idioma (monedas, XP,
 * logros) y porque así ni la app que manda los minutos ni la que los recibe
 * se conocen: las dos dependen solo del motor.
 *
 * Contrato de {@link #aportar}:
 * <ul>
 *   <li>Nunca lanza por una regla del destino: lo que no se puede aportar
 *       vuelve como {@link ResultadoAporte.Estado#RECHAZADO} con un código de
 *       motivo, que es una clave de los textos traducidos.</li>
 *   <li>Es idempotente por {@code clave}: la misma clave otra vez no suma ni
 *       paga nada y vuelve como {@link ResultadoAporte.Estado#REPETIDO}.</li>
 *   <li>Corre en su propia transacción: quien llama no debe tener una abierta
 *       que dependa de su resultado. Un fallo técnico (la base de datos caída)
 *       sí puede salir como excepción, para que quien llama lo reintente.</li>
 * </ul>
 */
public interface DestinoMinutos {

    /** El tipo de destino, tal como lo manda el cliente; cada dominio declara el suyo. */
    String tipo();

    /** Los destinos de este tipo a los que el usuario puede llevar minutos hoy. */
    List<DestinoDisponible> disponibles(Long usuarioId);

    /**
     * Suma {@code minutos} al destino en el día {@code dia} del usuario.
     *
     * @param clave  identifica el aporte: con la misma clave, REPETIDO
     * @param origen de dónde vienen los minutos (ej. "ENFOQUE")
     */
    ResultadoAporte aportar(Long usuarioId, Long destinoId, int minutos, UUID clave, LocalDate dia,
                            String origen);

    /**
     * Cómo va ahora el día {@code dia} del destino. Solo lee: no suma, no
     * paga y no cambia nada. Sirve para responder a un reintento con el
     * progreso actual. null si el destino no existe o no es del usuario.
     */
    ProgresoDestino progreso(Long usuarioId, Long destinoId, LocalDate dia);
}
