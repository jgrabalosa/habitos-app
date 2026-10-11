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

    /** El appId que el core envía en X-Norday-App para esta app. */
    private static final String APP = "habitos";

    // {codigo, nombre, descripcion, categoria, nivel, puntos}
    private static final String[][] LOGROS = {
            // Inicio
            {"PRIMER_HABITO", "Tu primer hábito propio", "Crea tu primer hábito personalizado", "Inicio", "Facil", "50"},
            {"PRIMERA_CATEGORIA", "Organizado desde el día 1", "Crea tu primera categoría personalizada", "Inicio", "Facil", "50"},
            {"PRIMEROS_PASOS", "Primeros pasos", "Completa tu primer hábito", "Inicio", "Facil", "50"},
            // Constancia
            {"RACHA_3", "A la tercera", "Completa un mismo hábito 3 veces", "Constancia", "Facil", "50"},
            {"RACHA_7", "Ya van siete", "Completa un mismo hábito 7 veces", "Constancia", "Medio", "100"},
            {"RACHA_RECUPERADA", "Resiliencia", "Vuelve a un hábito después de unos días sin hacerlo", "Constancia", "Medio", "100"},
            {"RACHA_10", "Dos dígitos", "Completa un mismo hábito 10 veces", "Constancia", "Medio", "100"},
            {"RACHA_15", "Quince firmes", "Completa un mismo hábito 15 veces", "Constancia", "Medio", "100"},
            {"RACHA_20", "Veinte hechas", "Completa un mismo hábito 20 veces", "Constancia", "Medio", "100"},
            {"RACHA_25", "Cuarto de cien", "Completa un mismo hábito 25 veces", "Constancia", "Medio", "100"},
            {"RACHA_30", "Coge forma", "Completa un mismo hábito 30 veces", "Constancia", "Dificil", "250"},
            {"RACHA_35", "Paso a paso", "Completa un mismo hábito 35 veces", "Constancia", "Medio", "100"},
            {"RACHA_40", "Cuarenta", "Completa un mismo hábito 40 veces", "Constancia", "Medio", "100"},
            {"RACHA_45", "Constancia probada", "Completa un mismo hábito 45 veces", "Constancia", "Medio", "100"},
            {"RACHA_50", "Medio centenar", "Completa un mismo hábito 50 veces", "Constancia", "Dificil", "250"},
            {"RACHA_55", "Sin mirar atrás", "Completa un mismo hábito 55 veces", "Constancia", "Medio", "100"},
            {"RACHA_60", "Sesenta", "Completa un mismo hábito 60 veces", "Constancia", "Medio", "100"},
            {"RACHA_65", "Rutina asentada", "Completa un mismo hábito 65 veces", "Constancia", "Medio", "100"},
            {"RACHA_70", "Setenta", "Completa un mismo hábito 70 veces", "Constancia", "Medio", "100"},
            {"RACHA_75", "Tres cuartos de cien", "Completa un mismo hábito 75 veces", "Constancia", "Medio", "100"},
            {"RACHA_80", "Ochenta", "Completa un mismo hábito 80 veces", "Constancia", "Medio", "100"},
            {"RACHA_85", "Recta final", "Completa un mismo hábito 85 veces", "Constancia", "Medio", "100"},
            {"RACHA_90", "Casi cien", "Completa un mismo hábito 90 veces", "Constancia", "Dificil", "250"},
            {"RACHA_100", "Maestro de la constancia", "Completa un mismo hábito 100 veces", "Constancia", "Dificil", "250"},
            {"RACHA_365", "Leyenda", "Completa un mismo hábito 365 veces", "Constancia", "Dificil", "250"},
            // Volumen
            {"HABITOS_ACTIVOS_3", "Coleccionista de hábitos", "Ten 3 hábitos activos a la vez", "Volumen", "Facil", "50"},
            {"HABITOS_ACTIVOS_5", "Vida equilibrada", "Ten 5 hábitos activos a la vez", "Volumen", "Medio", "100"},
            {"REGISTROS_100", "Cien no es nada", "Alcanza 100 registros completados en total", "Volumen", "Medio", "100"},
            {"REGISTROS_500", "Quinientos y contando", "Alcanza 500 registros completados en total", "Volumen", "Dificil", "250"},
            {"REGISTROS_1000", "Mil pasos", "Alcanza 1000 registros completados en total", "Volumen", "Dificil", "250"},
            // Variedad
            {"CATEGORIAS_3", "Explorador de categorías", "Usa 3 categorías distintas en tus hábitos", "Variedad", "Facil", "50"},
            {"CATEGORIAS_5", "Todoterreno", "Usa 5 categorías distintas", "Variedad", "Medio", "100"},
            // Exploración
            {"PRIMERA_NOTA", "Historias que contar", "Añade una nota a un registro", "Exploración", "Facil", "50"}
    };

    @Override
    public void run(String... args) {
        int creados = 0;
        for (String[] datos : LOGROS) {
            String codigo = datos[0];
            if (logroDAO.findByCodigo(codigo) != null) {
                continue; // ya existe, no lo tocamos
            }
            // origenApp explícito: sin él estos logros quedan como genéricos del
            // motor, y el catálogo del motor los daría por retirados al no
            // encontrarlos en SU lista.
            Logro logro = new Logro(datos[0], datos[1], datos[2], datos[3], datos[4],
                    Integer.parseInt(datos[5]), null, APP);
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
