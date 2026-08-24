package com.norday.core.service;

import com.norday.core.model.Usuario;
import java.util.Map;

/**
 * Contrato para que cada módulo aporte sus propios datos a la exportación
 * RGPD. Espejo de LimpiadorDatosUsuario: el motor no sabe qué tablas tiene
 * cada dominio, recolecta todas las implementaciones y las invoca.
 *
 * Cada módulo nuevo que guarde datos personales debe aportar su propia
 * implementación — igual que aporta su limpiador.
 */
public interface ExportadorDatosUsuario {

    /** Clave bajo la que cuelgan estos datos en el JSON final. */
    String seccion();

    Map<String, Object> exportar(Usuario usuario);
}
