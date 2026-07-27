package com.norday.core.config;

import com.norday.core.service.ProveedorMensajes;
import org.springframework.context.MessageSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.support.ReloadableResourceBundleMessageSource;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Construye el MessageSource a partir de los bundles que declara cada módulo.
 *
 * El motor no lista los basenames a mano: los recolecta. Así core no necesita
 * conocer qué módulos existen, y añadir una app al ecosistema no obliga a
 * tocar esta clase.
 */
@Configuration
public class MensajesConfig {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(MensajesConfig.class);

    @Bean
    public MessageSource messageSource(List<ProveedorMensajes> proveedores) {
        String[] basenames = proveedores.stream()
                .map(ProveedorMensajes::basename)
                .distinct()
                .map(b -> "classpath:" + b)
                .toArray(String[]::new);

        log.info("Bundles de mensajes registrados: {}", (Object) basenames);

        ReloadableResourceBundleMessageSource ms = new ReloadableResourceBundleMessageSource();
        ms.setBasenames(basenames);
        ms.setDefaultEncoding(StandardCharsets.UTF_8.name());
        // Si falta una traducción, se cae al bundle base (es) en vez de reventar
        ms.setFallbackToSystemLocale(false);
        ms.setUseCodeAsDefaultMessage(true);
        return ms;
    }
}
