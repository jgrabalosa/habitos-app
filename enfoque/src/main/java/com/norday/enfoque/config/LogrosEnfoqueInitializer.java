package com.norday.enfoque.config;

import com.norday.core.model.AppNorday;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.repository.ILogroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

/**
 * Siembra los logros propios de Norday Focus (16-nov-2026): sesiones, días,
 * racha, horas y horario. Los concede LogrosEnfoqueService al guardar una
 * sesión que cuenta.
 *
 * Mismo patrón que LogrosHabitosInitializer: por código, fila a fila, y con
 * {@code origenApp = enfoque} explícito. Sin él serían genéricos del motor y
 * CatalogoGamificacionInitializer los daría por retirados en el siguiente
 * arranque, al no encontrarlos en SU lista. Con él, el catálogo que pide
 * Norday Habits (X-Norday-App: habitos) no los ve y el de Norday Focus sí.
 *
 * El nombre y la descripción son la caída en español: la app traduce por
 * código. Las categorías son de las que ya traduce norday_flutter_core
 * (Inicio, Constancia, Volumen, Variedad): una nueva saldría sin traducir.
 */
@Component
public class LogrosEnfoqueInitializer implements CommandLineRunner {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(LogrosEnfoqueInitializer.class);

    /** El appId que el core envía en X-Norday-App para Norday Focus. */
    private static final String APP = AppNorday.ENFOQUE;

    public static final String PRIMERA_SESION = "ENFOQUE_PRIMERA_SESION";
    public static final String DIA = "ENFOQUE_DIA";
    public static final String CON_DESTINO = "ENFOQUE_CON_DESTINO";
    public static final String SESION_60 = "ENFOQUE_SESION_60";
    public static final String SESION_120 = "ENFOQUE_SESION_120";
    public static final String CUATRO = "ENFOQUE_CUATRO";
    public static final String SIN_RENDIRSE = "ENFOQUE_SIN_RENDIRSE";
    public static final String MANANA = "ENFOQUE_MANANA";
    public static final String NOCHE = "ENFOQUE_NOCHE";
    public static final String TOPE = "ENFOQUE_TOPE";

    /** Días distintos con sesión → código. */
    public static final int[] DIAS = {3, 7, 30, 100};
    /** Días seguidos con sesión → código. */
    public static final int[] RACHAS = {3, 7, 30};
    /** Horas en total → código. */
    public static final int[] HORAS = {5, 25, 100, 500};

    public static String dias(int n) { return "ENFOQUE_DIAS_" + n; }
    public static String racha(int n) { return "ENFOQUE_RACHA_" + n; }
    public static String horas(int n) { return "ENFOQUE_HORAS_" + n; }

    // {codigo, nombre, descripcion, categoria, nivel, puntos}
    static final String[][] LOGROS = {
            // Inicio
            {PRIMERA_SESION, "Primer foco", "Termina tu primera sesión de concentración", "Inicio", "Facil", "50"},
            {DIA, "Día de enfoque", "Llega a 50 minutos de concentración en un día", "Inicio", "Facil", "50"},
            {CON_DESTINO, "Minutos con destino", "Envía una sesión a un hábito de Norday Habits", "Inicio", "Facil", "50"},
            // Constancia: días distintos con sesión
            {dias(3), "Tres días", "Concéntrate 3 días distintos", "Constancia", "Facil", "50"},
            {dias(7), "Una semana de foco", "Concéntrate 7 días distintos", "Constancia", "Medio", "100"},
            {dias(30), "Un mes de foco", "Concéntrate 30 días distintos", "Constancia", "Dificil", "250"},
            {dias(100), "Cien días", "Concéntrate 100 días distintos", "Constancia", "Dificil", "250"},
            // Racha: días seguidos con sesión (categoría Constancia)
            {racha(3), "Tres seguidos", "Concéntrate 3 días seguidos", "Constancia", "Facil", "50"},
            {racha(7), "Siete seguidos", "Concéntrate 7 días seguidos", "Constancia", "Medio", "100"},
            {racha(30), "Treinta seguidos", "Concéntrate 30 días seguidos", "Constancia", "Dificil", "250"},
            // Volumen: horas en total
            {horas(5), "Cinco horas", "Suma 5 horas de concentración", "Volumen", "Facil", "50"},
            {horas(25), "Veinticinco horas", "Suma 25 horas de concentración", "Volumen", "Medio", "100"},
            {horas(100), "Cien horas", "Suma 100 horas de concentración", "Volumen", "Dificil", "250"},
            {horas(500), "Quinientas horas", "Suma 500 horas de concentración", "Volumen", "Dificil", "250"},
            // Sesión (categoría Volumen; SIN_RENDIRSE, Constancia)
            {SESION_60, "Una hora seguida", "Completa una sesión de 60 minutos o más", "Volumen", "Medio", "100"},
            {SESION_120, "Maratón", "Completa una sesión de 120 minutos", "Volumen", "Dificil", "250"},
            {CUATRO, "Cuatro seguidas", "Completa 4 sesiones en un día", "Volumen", "Medio", "100"},
            {SIN_RENDIRSE, "Sin rendirse", "Haz 10 sesiones seguidas sin perder ninguna", "Constancia", "Medio", "100"},
            {TOPE, "Al máximo", "Llega al tope de monedas de las sesiones en un día", "Volumen", "Medio", "100"},
            // Horario, en la zona del usuario (categoría Variedad)
            {MANANA, "A primera hora", "Empieza una sesión antes de las 8:00", "Variedad", "Facil", "50"},
            {NOCHE, "Turno de noche", "Empieza una sesión a las 22:00 o más tarde", "Variedad", "Facil", "50"},
    };

    @Autowired
    private ILogroDAO logroDAO;

    @Override
    public void run(String... args) {
        int creados = 0;
        for (String[] datos : LOGROS) {
            if (logroDAO.findByCodigo(datos[0]) != null) {
                continue; // ya existe, no lo tocamos
            }
            logroDAO.save(new Logro(datos[0], datos[1], datos[2], datos[3], datos[4],
                    Integer.parseInt(datos[5]), null, APP));
            creados++;
        }
        if (creados > 0) {
            log.info("Logros de Norday Focus nuevos creados: {}", creados);
        } else {
            log.info("Logros de Norday Focus: nada nuevo que crear (todos ya existían).");
        }
    }
}
