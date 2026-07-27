package com.norday.core.service;

/**
 * Cada módulo declara dónde están sus textos traducidos.
 *
 * El motor aporta la maquinaria (MessageSource, resolución de locale, envío)
 * pero no el contenido: no sabe ni cuántos módulos hay ni qué dicen sus
 * textos. Mismo patrón que LimpiadorDatosUsuario — Spring recolecta todas
 * las implementaciones y el motor las recorre.
 *
 * Un módulo nuevo del ecosistema solo tiene que aportar su bundle y su
 * implementación de esta interfaz.
 */
public interface ProveedorMensajes {

    /** Basename del bundle, sin idioma ni extensión. Ej: "mensajes/habitos". */
    String basename();
}
