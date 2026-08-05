package com.norday.habitos.config;

import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.repository.ILogroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra los logros del dominio "hábitos": rachas, volumen de registros,
 * hábitos activos, categorías y notas.
 *
 * La comprobación es por código, fila a fila (igual que el catálogo de
 * productos), y no la guarda todo-o-nada que tenía DataInitializer: los
 * logros viven ahora en dos initializers de módulos distintos, y un
 * findAll().isEmpty() compartido haría que el segundo en arrancar no
 * sembrara nada.
 */
@Component
public class LogrosHabitosInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LogrosHabitosInitializer.class);

    @Autowired
    private ILogroDAO logroDAO;

    // {codigo, nombre, descripcion, categoria, nivel, puntos}
    private static final String[][] LOGROS = {
            // Inicio
            {"PRIMER_HABITO", "Tu primer hábito propio", "Crea tu primer hábito personalizado", "Inicio", "Facil", "100"},
            {"PRIMERA_CATEGORIA", "Organizado desde el día 1", "Crea tu primera categoría personalizada", "Inicio", "Facil", "100"},
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
            {"PRIMERA_NOTA", "Historias que contar", "Añade una nota a un registro", "Exploración", "Facil", "100"}
    };

    @Override
    public void run(String... args) {
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
            log.info("Logros de hábitos nuevos creados: {}", creados);
        } else {
            log.info("Logros de hábitos: nada nuevo que crear (todos ya existían).");
        }
    }
}
