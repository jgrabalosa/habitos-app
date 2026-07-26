package com.norday.habitos.config;

import com.norday.habitos.model.Categoria;
import com.norday.habitos.repository.ICategoriaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

/** Siembra las categorías globales del dominio "hábitos". */
@Component
public class CategoriasInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CategoriasInitializer.class);

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Override
    public void run(String... args) {
        List<Categoria> existentes = categoriaDAO.findGlobales();
        if (!existentes.isEmpty()) {
            return; // Ya están creadas, no hacemos nada
        }

        // {nombre, descripcion, icono, color}
        String[][] categorias = {
                {"Salud", "Hábitos relacionados con tu salud general", "❤️", "#E05252"},
                {"Deporte", "Hábitos de ejercicio y actividad física", "🏃", "#27C76F"},
                {"Alimentación", "Hábitos de nutrición y dieta", "🥗", "#6EE7A8"},
                {"Mente", "Mindfulness, meditación y bienestar mental", "🧘", "#8B7EC8"},
                {"Trabajo", "Productividad y hábitos laborales", "💼", "#23395D"},
                {"Estudio", "Hábitos de aprendizaje y formación", "📚", "#3B82C4"},
                {"Finanzas", "Hábitos de ahorro y gestión económica", "💰", "#D4A843"},
                {"Social", "Hábitos relacionados con relaciones personales", "👥", "#E8875B"},
                {"Creatividad", "Hábitos creativos y artísticos", "🎨", "#C75B9B"},
                {"Sueño", "Hábitos de descanso y sueño", "🌙", "#5B6EE8"}
        };

        int orden = 1;
        for (String[] datos : categorias) {
            Categoria categoria = new Categoria(datos[0], datos[1], datos[3], datos[2], true, orden, null);
            categoriaDAO.save(categoria);
            orden++;
        }

        log.info("Categorías globales creadas correctamente ({})", categorias.length);
    }
}
