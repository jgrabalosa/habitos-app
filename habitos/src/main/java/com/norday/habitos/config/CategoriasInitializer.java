package com.norday.habitos.config;

import com.norday.habitos.model.Categoria;
import com.norday.habitos.repository.ICategoriaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra las categorías globales del dominio "hábitos".
 *
 * El nombre que va aquí es solo la caída: el cliente traduce por el código.
 * Las categorías que crea el usuario no llevan código — su nombre lo ha
 * escrito él y se muestra tal cual.
 *
 * La comprobación es por código, fila a fila, igual que logros y productos:
 * añadir una categoría nueva a la lista la crea en el próximo arranque sin
 * vaciar la tabla, y arrancar dos veces no duplica nada.
 */
@Component
public class CategoriasInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CategoriasInitializer.class);

    @Autowired
    private ICategoriaDAO categoriaDAO;

    // {codigo, nombre, descripcion, icono, color}
    private static final String[][] CATEGORIAS = {
            {"CAT_SALUD", "Salud", "Hábitos relacionados con tu salud general", "❤️", "#E05252"},
            {"CAT_DEPORTE", "Deporte", "Hábitos de ejercicio y actividad física", "🏃", "#27C76F"},
            {"CAT_ALIMENTACION", "Alimentación", "Hábitos de nutrición y dieta", "🥗", "#6EE7A8"},
            {"CAT_MENTE", "Mente", "Mindfulness, meditación y bienestar mental", "🧘", "#8B7EC8"},
            {"CAT_TRABAJO", "Trabajo", "Productividad y hábitos laborales", "💼", "#23395D"},
            {"CAT_ESTUDIO", "Estudio", "Hábitos de aprendizaje y formación", "📚", "#3B82C4"},
            {"CAT_FINANZAS", "Finanzas", "Hábitos de ahorro y gestión económica", "💰", "#D4A843"},
            {"CAT_SOCIAL", "Social", "Hábitos relacionados con relaciones personales", "👥", "#E8875B"},
            {"CAT_CREATIVIDAD", "Creatividad", "Hábitos creativos y artísticos", "🎨", "#C75B9B"},
            {"CAT_SUENO", "Sueño", "Hábitos de descanso y sueño", "🌙", "#5B6EE8"}
    };

    @Override
    public void run(String... args) {
        int creadas = 0;
        int orden = 1;
        for (String[] datos : CATEGORIAS) {
            String codigo = datos[0];
            if (categoriaDAO.findByCodigo(codigo) == null) {
                // Categoria(codigo, nombre, descripcion, color, icono, esGlobal, orden, creador)
                categoriaDAO.save(new Categoria(codigo, datos[1], datos[2], datos[4], datos[3], true, orden, null));
                creadas++;
            }
            orden++;
        }

        if (creadas > 0) {
            log.info("Categorías globales nuevas creadas: {}", creadas);
        } else {
            log.info("Categorías globales: nada nuevo que crear (todas ya existían).");
        }
    }
}
