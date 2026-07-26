package com.norday.gamificacion.config;

import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.Producto;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra el catálogo genérico de gamificación: los logros que no dependen
 * de ningún dominio (cuenta, perfil, reseña) y la tienda de productos.
 *
 * Los logros se comprueban por código, fila a fila: el catálogo de logros
 * lo siembran dos módulos distintos, y una guarda todo-o-nada sobre la
 * tabla entera haría que el segundo en arrancar no sembrara nada.
 */
@Component
public class CatalogoGamificacionInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(CatalogoGamificacionInitializer.class);

    @Autowired
    private ILogroDAO logroDAO;

    @Autowired
    private IProductoDAO productoDAO;

    // {codigo, nombre, descripcion, categoria, nivel, puntos}
    private static final String[][] LOGROS = {
            // Inicio
            {"BIENVENIDO", "Bienvenido/a", "Personaliza tu perfil de usuario", "Inicio", "Facil", "100"},
            {"LOGIN_GOOGLE", "Conectado con Google", "Inicia sesión usando tu cuenta de Google", "Inicio", "Facil", "100"},
            // Exploración
            {"INTERACCION_RESENA", "Tu opinión cuenta", "Interactúa con la valoración de la app en Google Play", "Exploración", "Facil", "100"}
    };

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

    @Override
    public void run(String... args) {
        inicializarLogros();
        inicializarProductos();
    }

    private void inicializarLogros() {
        int creados = 0;
        for (String[] datos : LOGROS) {
            String codigo = datos[0];
            if (logroDAO.findByCodigo(codigo) != null) {
                continue; // ya existe, no lo tocamos
            }
            Logro logro = new Logro(datos[0], datos[1], datos[2], datos[3], datos[4],
                    Integer.parseInt(datos[5]), null);
            logroDAO.save(logro);
            creados++;
        }

        if (creados > 0) {
            log.info("Logros genéricos nuevos creados: {}", creados);
        } else {
            log.info("Logros genéricos: nada nuevo que crear (todos ya existían).");
        }
    }

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
