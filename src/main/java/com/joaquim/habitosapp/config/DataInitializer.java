package com.joaquim.habitosapp.config;

import com.joaquim.habitosapp.model.Categoria;
import com.joaquim.habitosapp.repository.ICategoriaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Override
    public void run(String... args) {
        List<Categoria> existentes = categoriaDAO.findGlobales();
        if (!existentes.isEmpty()) {
            return; // Ya están creadas, no hacemos nada
        }

        String[][] categorias = {
                {"Salud", "Hábitos relacionados con tu salud general"},
                {"Deporte", "Hábitos de ejercicio y actividad física"},
                {"Alimentación", "Hábitos de nutrición y dieta"},
                {"Mente", "Mindfulness, meditación y bienestar mental"},
                {"Trabajo", "Productividad y hábitos laborales"},
                {"Estudio", "Hábitos de aprendizaje y formación"},
                {"Finanzas", "Hábitos de ahorro y gestión económica"},
                {"Social", "Hábitos relacionados con relaciones personales"},
                {"Creatividad", "Hábitos creativos y artísticos"},
                {"Sueño", "Hábitos de descanso y sueño"}
        };

        int orden = 1;
        for (String[] datos : categorias) {
            Categoria categoria = new Categoria(datos[0], datos[1], null, null, true, orden, null);
            categoriaDAO.save(categoria);
            orden++;
        }

        System.out.println("Categorías globales creadas correctamente (" + categorias.length + ")");
    }
}