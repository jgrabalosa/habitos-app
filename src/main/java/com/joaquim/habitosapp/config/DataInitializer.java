package com.joaquim.habitosapp.config;

import com.joaquim.habitosapp.model.Categoria;
import com.joaquim.habitosapp.model.Logro;
import com.joaquim.habitosapp.model.Producto;
import com.joaquim.habitosapp.repository.ICategoriaDAO;
import com.joaquim.habitosapp.repository.ILogroDAO;
import com.joaquim.habitosapp.repository.IProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class DataInitializer implements CommandLineRunner {

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private ILogroDAO logroDAO;

    @Autowired
    private IProductoDAO productoDAO;

    @Override
    public void run(String... args) {
        inicializarCategorias();
        inicializarLogros();
        inicializarProductos();
    }

    private void inicializarCategorias() {
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

    private void inicializarLogros() {
        List<Logro> existentes = logroDAO.findAll();
        if (!existentes.isEmpty()) {
            return; // Ya están creados, no hacemos nada
        }

        // {nombre, descripcion, categoria, nivel, puntos}
        String[][] logros = {
                // Inicio
                {"Primeros pasos", "Completa tu primer hábito", "Inicio", "Facil", "100"},
                {"Bienvenido/a", "Personaliza tu perfil de usuario", "Inicio", "Facil", "100"},
                {"Tu primer hábito propio", "Crea tu primer hábito personalizado", "Inicio", "Facil", "100"},
                {"Organizado desde el día 1", "Crea tu primera categoría personalizada", "Inicio", "Facil", "100"},
                {"Conectado con Google", "Inicia sesión usando tu cuenta de Google", "Inicio", "Facil", "100"},
                // Constancia
                {"En racha", "Alcanza tu primera racha de 3", "Constancia", "Facil", "100"},
                {"Buen ritmo", "Alcanza una racha de 7", "Constancia", "Medio", "200"},
                {"Resiliencia", "Recupera un hábito tras romper una racha y vuelve a alcanzar 3", "Constancia", "Medio", "200"},
                {"Imparable", "Alcanza una racha de 30", "Constancia", "Dificil", "500"},
                {"Maestro de la constancia", "Alcanza una racha de 100", "Constancia", "Dificil", "500"},
                {"Leyenda", "Alcanza una racha de 365", "Constancia", "Dificil", "500"},
                // Volumen
                {"Coleccionista de hábitos", "Ten 3 hábitos activos a la vez", "Volumen", "Facil", "100"},
                {"Vida equilibrada", "Ten 5 hábitos activos a la vez", "Volumen", "Medio", "200"},
                {"Cien no es nada", "Alcanza 100 registros completados en total", "Volumen", "Medio", "200"},
                {"Quinientos y contando", "Alcanza 500 registros completados en total", "Volumen", "Dificil", "500"},
                {"Mil pasos", "Alcanza 1000 registros completados en total", "Volumen", "Dificil", "500"},
                // Variedad
                {"Explorador de categorías", "Usa 3 categorías distintas en tus hábitos", "Variedad", "Facil", "100"},
                {"Todoterreno", "Usa 5 categorías distintas", "Variedad", "Medio", "200"},
                {"Mezcla de frecuencias", "Ten a la vez un hábito diario, uno semanal y uno mensual activos", "Variedad", "Medio", "200"},
                {"Diseñador de hábitos", "Crea 3 categorías personalizadas propias", "Variedad", "Medio", "200"},
                // Exploración
                {"Historias que contar", "Añade una nota a un registro", "Exploración", "Facil", "100"},
                {"Diario detallado", "Añade notas en 10 registros distintos", "Exploración", "Medio", "200"},
                {"Vista completa", "Consulta el detalle de un hábito (heatmap) por primera vez", "Exploración", "Facil", "100"},
                {"Perfeccionista", "Edita un hábito existente", "Exploración", "Facil", "100"},
                {"Depurador", "Elimina un hábito que ya no te sirve", "Exploración", "Facil", "100"}
        };

        for (String[] datos : logros) {
            Logro logro = new Logro(datos[0], datos[1], datos[2], datos[3],
                    Integer.parseInt(datos[4]), null);
            logroDAO.save(logro);
        }

        System.out.println("Logros creados correctamente (" + logros.length + ")");
    }

    private void inicializarProductos() {
        List<Producto> existentes = productoDAO.findAll();
        if (!existentes.isEmpty()) {
            return; // Ya están creados, no hacemos nada
        }

        Producto escudoRacha = new Producto(
                "Escudo de racha",
                "Protege tu racha durante 1 día si olvidas completar tu hábito",
                "Protección",
                "CONSUMIBLE",
                300,
                null
        );
        productoDAO.save(escudoRacha);

        System.out.println("Productos creados correctamente (1)");
    }
}