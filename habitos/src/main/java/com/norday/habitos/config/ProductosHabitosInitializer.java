package com.norday.habitos.config;

import com.norday.core.model.AppNorday;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.repository.IProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra los productos propios de la app de hábitos. El catálogo del motor
 * solo retira los productos sin origenApp, así que estos no los toca.
 */
@Component
public class ProductosHabitosInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(ProductosHabitosInitializer.class);

    /** Pausar un hábito gasta una. Se compra en la tienda, tantas como se quiera. */
    public static final String PAUSA = "PAUSA_HABITO";

    /** El appId que el core envía en X-Norday-App para esta app. */
    private static final String APP = AppNorday.HABITOS;

    @Autowired
    private IProductoDAO productoDAO;

    @Override
    public void run(String... args) {
        if (productoDAO.findByCodigo(PAUSA) != null) {
            return; // ya existe, no lo tocamos
        }
        // Precio del rebalanceo del 24-nov-2026 (V34, D-71). Cambiarlo en una
        // fila que ya existe exige migración: este método no la toca.
        productoDAO.save(new Producto(PAUSA, "Pausa",
                "Pausa un hábito sin perder su ritmo: no sale en Hoy ni te avisa hasta que lo reanudes",
                "Protección", "CONSUMIBLE", 350, null, APP));
        log.info("Producto de hábitos creado: {}", PAUSA);
    }
}
