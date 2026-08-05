package com.norday;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * La JVM ya no fija zona por defecto.
 *
 * Antes había un @PostConstruct que hacía
 * TimeZone.setDefault(TimeZone.getTimeZone("Europe/Madrid")), y de ahí
 * heredaban su "hoy" todos los cálculos. Con usuarios en varias zonas eso
 * deja de tener sentido: cada cálculo de cara al usuario resuelve la zona
 * desde el propio usuario (ZonaUsuarioService), y los sellos de auditoría
 * van explícitamente en UTC.
 *
 * El contenedor debe fijar TZ=UTC en el despliegue, para que lo que quede
 * sin zona explícita no dependa del host.
 */
@SpringBootApplication
@EnableScheduling
public class NordayApplication {

    public static void main(String[] args) {
        SpringApplication.run(NordayApplication.class, args);
    }

}
