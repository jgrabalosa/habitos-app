package com.norday.conocimiento.model;

/**
 * Estado de una píldora para un usuario concreto — el bucle principal de la
 * app.
 *
 * EN_COLA y LEIDA llegan con el producto mínimo (V37-V39): EN_COLA es el match
 * a la derecha en Descubrir (la píldora espera en la cola de lectura) y LEIDA,
 * la lectura terminada. La columna es VARCHAR(30) sin CHECK en las bases de
 * Flyway; V37 quita el CHECK que traen las tablas generadas por Hibernate.
 * Todavía no los escribe nadie; los usan las tareas siguientes, que también
 * tienen que tocar las consultas que tratan VISTA y GUARDADA como «ya leída»
 * (PildoraDAO.findActivasElegibles, UsuarioPildoraDAO.findParaColeccion).
 */
public enum EstadoPildora {
    DESCARTADA,
    VISTA,
    GUARDADA,
    EN_COLA,
    LEIDA
}
