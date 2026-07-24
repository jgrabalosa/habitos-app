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

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(DataInitializer.class);

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

    private void inicializarLogros() {
        List<Logro> existentes = logroDAO.findAll();
        if (!existentes.isEmpty()) {
            return; // Ya están creados, no hacemos nada
        }

        // {codigo, nombre, descripcion, categoria, nivel, puntos}
        String[][] logros = {
                // Inicio
                {"PRIMER_HABITO", "Tu primer hábito propio", "Crea tu primer hábito personalizado", "Inicio", "Facil", "100"},
                {"BIENVENIDO", "Bienvenido/a", "Personaliza tu perfil de usuario", "Inicio", "Facil", "100"},
                {"PRIMERA_CATEGORIA", "Organizado desde el día 1", "Crea tu primera categoría personalizada", "Inicio", "Facil", "100"},
                {"LOGIN_GOOGLE", "Conectado con Google", "Inicia sesión usando tu cuenta de Google", "Inicio", "Facil", "100"},
                {"PRIMEROS_PASOS", "Primeros pasos", "Completa tu primer hábito", "Inicio", "Facil", "100"},
                // Constancia
                {"RACHA_3", "En racha", "Alcanza tu primera racha de 3", "Constancia", "Facil", "100"},
                {"RACHA_7", "Buen ritmo", "Alcanza una racha de 7", "Constancia", "Medio", "200"},
                {"RACHA_RECUPERADA", "Resiliencia", "Recupera un hábito tras romper una racha y vuelve a alcanzar 3", "Constancia", "Medio", "200"},
                {"RACHA_30", "Imparable", "Alcanza una racha de 30", "Constancia", "Dificil", "500"},
                {"RACHA_100", "Maestro de la constancia", "Alcanza una racha de 100", "Constancia", "Dificil", "500"},
                {"RACHA_365", "Leyenda", "Alcanza una racha de 365", "Constancia", "Dificil", "500"},
                // Volumen
                {"HABITOS_ACTIVOS_3", "Coleccionista de hábitos", "Ten 3 hábitos activos a la vez", "Volumen", "Facil", "100"},
                {"HABITOS_ACTIVOS_5", "Vida equilibrada", "Ten 5 hábitos activos a la vez", "Volumen", "Medio", "200"},
                {"REGISTROS_100", "Cien no es nada", "Alcanza 100 registros completados en total", "Volumen", "Medio", "200"},
                {"REGISTROS_500", "Quinientos y contando", "Alcanza 500 registros completados en total", "Volumen", "Dificil", "500"},
                {"REGISTROS_1000", "Mil pasos", "Alcanza 1000 registros completados en total", "Volumen", "Dificil", "500"},
                // Variedad
                {"CATEGORIAS_3", "Explorador de categorías", "Usa 3 categorías distintas en tus hábitos", "Variedad", "Facil", "100"},
                {"CATEGORIAS_5", "Todoterreno", "Usa 5 categorías distintas", "Variedad", "Medio", "200"},
                // Exploración
                {"PRIMERA_NOTA", "Historias que contar", "Añade una nota a un registro", "Exploración", "Facil", "100"},
                {"INTERACCION_RESENA", "Tu opinión cuenta", "Interactúa con la valoración de la app en Google Play", "Exploración", "Facil", "100"}
        };

        for (String[] datos : logros) {
            Logro logro = new Logro(datos[0], datos[1], datos[2], datos[3], datos[4],
                    Integer.parseInt(datos[5]), null);
            logroDAO.save(logro);
        }

        log.info("Logros creados correctamente ({})", logros.length);
    }

    // {codigo, nombre, descripcion, categoria, tipo, precio}
    // Se comprueba producto a producto por su código: añadir una fila nueva
    // aquí la crea en el próximo arranque aunque la tabla ya tenga datos.
    private static final String[][] PRODUCTOS = {
            {"ESCUDO_RACHA", "Escudo de racha", "Protege tu racha durante 1 día si olvidas completar tu hábito", "Protección", "CONSUMIBLE", "300"},
            // Regalo de bienvenida: todo usuario los posee desde el registro (ver ProductoService.otorgarTemasBasicosGratis).
            // Precio 0: nunca se compran, solo se equipan/desequipan como cualquier otro tema.
            {"TEMA_BASICO_CLARO", "Básico Claro", "El tema claro de serie de Norday", "Tema", "EQUIPABLE", "0"},
            {"TEMA_BASICO_OSCURO", "Básico Oscuro", "El tema oscuro de serie de Norday", "Tema", "EQUIPABLE", "0"},
            // precio provisional, se reajustará en el punto 7 de la Fase 2
            {"TEMA_CALIDEZ", "Calidez", "Un tema premium con tonos cálidos y acogedores", "Tema", "EQUIPABLE", "1000"},
            {"TEMA_NEOTOKYO", "Neo-Tokyo", "Un tema premium inspirado en la estética anime y neón", "Tema", "EQUIPABLE", "1000"},
            {"TEMA_OCEANO", "Océano", "Un tema premium con tonos azules y frescos del mar", "Tema", "EQUIPABLE", "1000"},
            {"TEMA_BOSQUE", "Bosque", "Un tema premium con tonos verdes y naturales", "Tema", "EQUIPABLE", "1000"},
            {"TEMA_COBRE", "Cobre Nocturno", "Un tema premium elegante en azul noche y cobre", "Tema", "EQUIPABLE", "1000"},
            // placeholder hasta que revises los DiceBear reales
            {"AVATAR_ZORRO", "Zorro", "Avatar ilustrado de zorro", "Avatar", "EQUIPABLE", "500"},
            {"AVATAR_GATO", "Gato", "Avatar ilustrado de gato", "Avatar", "EQUIPABLE", "500"},
            {"AVATAR_BUHO", "Búho", "Avatar ilustrado de búho", "Avatar", "EQUIPABLE", "500"},
            {"AVATAR_PANDA", "Panda", "Avatar ilustrado de panda", "Avatar", "EQUIPABLE", "500"},
            {"AVATAR_TORTUGA", "Tortuga", "Avatar ilustrado de tortuga", "Avatar", "EQUIPABLE", "500"},
            // placeholder: falta imagen/icono real, precio provisional a reajustar en el punto 8
            {"COMIDA_BASICA", "Comida", "Alimenta a tu mascota y gana experiencia", "Consumible", "CONSUMIBLE", "50"},
    };

    private void inicializarProductos() {
        int creados = 0;
        for (String[] datos : PRODUCTOS) {
            String codigo = datos[0];
            if (productoDAO.findByCodigo(codigo) != null) {
                continue; // ya existe, no lo tocamos
            }
            Producto producto = new Producto(
                    codigo,
                    datos[1],
                    datos[2],
                    datos[3],
                    datos[4],
                    Integer.parseInt(datos[5]),
                    null
            );
            productoDAO.save(producto);
            creados++;
        }

        if (creados > 0) {
            log.info("Productos nuevos creados: {}", creados);
        } else {
            log.info("Productos: nada nuevo que crear (todos ya existían).");
        }
    }
}