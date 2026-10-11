package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.service.LogroService;
import com.norday.habitos.model.CalculoPremio;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Logros específicos del dominio "hábitos": rachas, volumen de registros,
 * hábitos activos, categorías y notas. El otorgado en sí lo hace el motor
 * genérico (LogroService); aquí solo vive la decisión de qué código toca.
 */
@Service
public class LogrosHabitosService {

    @Autowired
    private LogroService logroService;

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    // B3: los logros de constancia ya no van por racha (ver DIAS_HECHOS_CON_LOGRO).
    // Se conserva comentado.
//    /**
//     * Umbrales de racha que otorgan logro. El código se compone como
//     * "RACHA_" + umbral, así que cada valor de aquí exige una fila con ese
//     * código en LogrosHabitosInitializer: sin ella el logro no existe y el
//     * otorgado se pierde en silencio.
//     *
//     * Entre 7 y 30 se va de 5 en 5 y se sigue así hasta 90, que es el tramo
//     * donde más gente abandona. RACHA_3 y RACHA_RECUPERADA NO están aquí:
//     * dependen además de rachaMaxima y viven en sus propios ifs.
//     *
//     * La comparación es por igualdad, no por >=, igual que antes: si una
//     * racha saltara de 34 a 36 sin pasar por 35, ese logro se perdería.
//     */
//    private static final int[] UMBRALES_RACHA = {
//            7, 10, 15, 20, 25, 30, 35, 40, 45, 50,
//            55, 60, 65, 70, 75, 80, 85, 90, 100, 365
//    };

    /**
     * Días hechos de un mismo hábito que otorgan logro, de menor a mayor.
     * El código se compone como "RACHA_" + número: los códigos no cambian
     * aunque ya no midan rachas. Cada valor de aquí exige una fila con ese
     * código en LogrosHabitosInitializer: sin ella el logro no existe y el
     * otorgado se pierde en silencio.
     */
    private static final int[] DIAS_HECHOS_CON_LOGRO = {
            3, 7, 10, 15, 20, 25, 30, 35, 40, 45, 50,
            55, 60, 65, 70, 75, 80, 85, 90, 100, 365
    };

    // ── Evento: completar un registro ───────────────────────
    public List<String> evaluarTrasCompletarRegistro(Usuario usuario, Habito habito) {
        List<String> logrosOtorgados = new ArrayList<>();
        logrosOtorgados.addAll(comprobarLogrosDeDiasHechos(usuario, habito));
        logrosOtorgados.addAll(comprobarLogrosDeVolumen(usuario));
        return logrosOtorgados;
    }

    // ── Evento: volver a un hábito tras un tiempo sin hacerlo ──
    // Cuándo un paso es una vuelta lo decide CalculoPremio.esUnaVuelta; aquí
    // solo se concede el logro, que se gana una vez.
    public List<String> evaluarTrasVolver(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        if (logroService.otorgarSiNoTiene(usuario, "RACHA_RECUPERADA")) otorgados.add("RACHA_RECUPERADA");
        return otorgados;
    }

    // B3: los logros de constancia ya no van por racha (ver comprobarLogrosDeDiasHechos).
    // Se conserva comentado. Resiliencia (RACHA_RECUPERADA) tendrá regla nueva en su propio paso.
//    private List<String> comprobarLogrosDeRacha(Usuario usuario, Habito habito) {
//        List<String> otorgados = new ArrayList<>();
//        Racha racha = rachaDAO.findByHabito(habito);
//        if (racha == null) return otorgados;
//
//        int actual = racha.getRachaActual();
//        int maxima = racha.getRachaMaxima();
//
//        if (actual == 3 && maxima == actual) {
//            if (logroService.otorgarSiNoTiene(usuario, "RACHA_3")) otorgados.add("RACHA_3");
//        }
//        if (actual == 3 && maxima > actual) {
//            if (logroService.otorgarSiNoTiene(usuario, "RACHA_RECUPERADA")) otorgados.add("RACHA_RECUPERADA");
//        }
//        for (int umbral : UMBRALES_RACHA) {
//            if (actual == umbral) {
//                String codigo = "RACHA_" + umbral;
//                if (logroService.otorgarSiNoTiene(usuario, codigo)) otorgados.add(codigo);
//                break;
//            }
//        }
//        return otorgados;
//    }

    /**
     * Los logros de constancia: "haz un mismo hábito N días", en total y sin
     * importar los huecos. Se conceden todos los escalones alcanzados o ya
     * superados que el usuario no tenga: el motor nunca repite un logro, así
     * que no hace falta acertar el día exacto ni guardar por dónde se va.
     * Un día hecho es la misma unidad que en los puntos y los hitos.
     */
    private List<String> comprobarLogrosDeDiasHechos(Usuario usuario, Habito habito) {
        List<String> otorgados = new ArrayList<>();
        List<LocalDate> fechas = new ArrayList<>();
        for (Registro registro : registroDAO.findByHabito(habito)) {
            if (registro.isCompletado()) fechas.add(registro.getFecha());
        }
        int diasHechos = CalculoPremio.diasHechos(habito.getFrecuencia(), habito.getMeta(), fechas);

        for (int dias : DIAS_HECHOS_CON_LOGRO) {
            if (dias > diasHechos) break;
            String codigo = "RACHA_" + dias;
            if (logroService.otorgarSiNoTiene(usuario, codigo)) otorgados.add(codigo);
        }
        return otorgados;
    }

    private List<String> comprobarLogrosDeVolumen(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        int totalRegistros = registroDAO.contarPorUsuario(usuario.getUsuarioId());

        if (totalRegistros == 1) {
            if (logroService.otorgarSiNoTiene(usuario, "PRIMEROS_PASOS")) otorgados.add("PRIMEROS_PASOS");
        }
        if (totalRegistros == 100) {
            if (logroService.otorgarSiNoTiene(usuario, "REGISTROS_100")) otorgados.add("REGISTROS_100");
        }
        if (totalRegistros == 500) {
            if (logroService.otorgarSiNoTiene(usuario, "REGISTROS_500")) otorgados.add("REGISTROS_500");
        }
        if (totalRegistros == 1000) {
            if (logroService.otorgarSiNoTiene(usuario, "REGISTROS_1000")) otorgados.add("REGISTROS_1000");
        }
        return otorgados;
    }

    // ── Evento: crear un hábito ──────────────────────────────
    public List<String> evaluarTrasCrearHabito(Usuario usuario) {
        List<String> otorgados = new ArrayList<>();
        List<Habito> activos = habitoDAO.findActivos(usuario);

        if (activos.size() == 1) {
            if (logroService.otorgarSiNoTiene(usuario, "PRIMER_HABITO")) otorgados.add("PRIMER_HABITO");
        }
        if (activos.size() == 3) {
            if (logroService.otorgarSiNoTiene(usuario, "HABITOS_ACTIVOS_3")) otorgados.add("HABITOS_ACTIVOS_3");
        }
        if (activos.size() == 5) {
            if (logroService.otorgarSiNoTiene(usuario, "HABITOS_ACTIVOS_5")) otorgados.add("HABITOS_ACTIVOS_5");
        }

        otorgados.addAll(comprobarCategoriasUsadas(usuario, activos));
        return otorgados;
    }

    private List<String> comprobarCategoriasUsadas(Usuario usuario, List<Habito> activos) {
        List<String> otorgados = new ArrayList<>();
        Set<Integer> categoriasDistintas = new HashSet<>();
        for (Habito h : activos) {
            if (h.getTipo() != null) {
                categoriasDistintas.add(h.getTipo().getCategoriaId());
            }
        }

        if (categoriasDistintas.size() == 3) {
            if (logroService.otorgarSiNoTiene(usuario, "CATEGORIAS_3")) otorgados.add("CATEGORIAS_3");
        }
        if (categoriasDistintas.size() == 5) {
            if (logroService.otorgarSiNoTiene(usuario, "CATEGORIAS_5")) otorgados.add("CATEGORIAS_5");
        }
        return otorgados;
    }

    // ── Evento: crear categoría personalizada ────────────────
    public void evaluarTrasCrearCategoria(Usuario usuario, List<Categoria> categoriasCreadorUsuario) {
        if (categoriasCreadorUsuario.size() == 1) {
            logroService.otorgarSiNoTiene(usuario, "PRIMERA_CATEGORIA");
        }
    }

    // ── Evento: añadir nota a registro ───────────────────────
    public void evaluarTrasAnadirNota(Usuario usuario) {
        logroService.otorgarSiNoTiene(usuario, "PRIMERA_NOTA");
    }
}
