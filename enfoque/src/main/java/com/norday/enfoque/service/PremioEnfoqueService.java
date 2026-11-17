package com.norday.enfoque.service;

import com.norday.core.model.Usuario;
import com.norday.enfoque.model.ParametrosPremioEnfoque;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.service.LogroService;
import com.norday.gamificacion.service.MascotaService;
import com.norday.gamificacion.service.UsuarioMonedaService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.norday.enfoque.model.ParametrosPremioEnfoque.TOPE_MONEDAS_DIA;
import static com.norday.enfoque.model.ParametrosPremioEnfoque.TOPE_XP_DIA;

/**
 * Lo que paga una sesión de Norday Focus por sí misma (16-nov-2026): monedas
 * y XP de Nori, tenga hábito destino o no, más los logros de Focus. Es aparte
 * de lo que paga el hábito destino al recibir los minutos (el aporte), y se
 * suman.
 *
 * Se paga en el alta de la sesión, dentro de su transacción y con el usuario
 * ya bloqueado (SesionEnfoqueService.guardar): el tope del día se calcula sin
 * que otra sesión se cuele en medio, y si algo falla no queda ni la sesión ni
 * el premio, y el reintento con la misma clave lo hace entero. Un reintento de
 * una sesión ya guardada devuelve el premio guardado y no paga otra vez.
 *
 * Las cifras están en ParametrosPremioEnfoque.
 */
@Service
public class PremioEnfoqueService {

    /** El origen de las monedas en el libro (usuario_moneda). */
    public static final String ORIGEN_MONEDAS = "SESION_ENFOQUE";

    @Autowired
    private ISesionEnfoqueDAO sesionDAO;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    @Autowired
    private MascotaService mascotaService;

    @Autowired
    private LogroService logroService;

    @Autowired
    private LogrosEnfoqueService logrosEnfoqueService;

    /**
     * El premio de una sesión: lo que pagó ella misma (monedas y XP, ya
     * recortadas por el tope) y los logros que desbloqueó, también los de
     * fase de Nori por su XP. {@code topeAlcanzado}: si con esta sesión el
     * día llegó al tope de monedas.
     */
    public record Premio(int monedas, int xp, List<String> logros, boolean topeAlcanzado) {
        public static final Premio NADA = new Premio(0, 0, List.of(), false);
    }

    /**
     * Paga el premio de una sesión recién guardada y lo apunta en ella. Solo
     * para el alta: quien llama tiene abierta la transacción del alta y el
     * usuario bloqueado. Una sesión que no cuenta (PERDIDA) no paga nada.
     */
    public Premio pagar(Usuario usuario, SesionEnfoque sesion) {
        if (!sesion.getEstado().cuenta()) {
            return Premio.NADA;
        }
        int usuarioId = usuario.getUsuarioId();
        // Los logros, como «después − antes»: es seguro porque el usuario
        // está bloqueado y todo lo que concede logros lo bloquea antes.
        Set<String> antes = codigosDeLogros(usuario);

        int minutos = sesion.minutos();
        int[] pagado = sesionDAO.premiadoDelDia(usuarioId, sesion.getDia());
        int monedas = 0;
        int xp = 0;
        // Con el tope de monedas ya alcanzado no se paga nada, tampoco XP: el
        // tope es «lo que pagan las sesiones hoy», no dos contadores sueltos.
        if (pagado[0] < TOPE_MONEDAS_DIA) {
            monedas = recortar(ParametrosPremioEnfoque.monedasDe(sesion.getEstado(), minutos), pagado[0],
                    TOPE_MONEDAS_DIA);
            xp = recortar(ParametrosPremioEnfoque.xpDe(sesion.getEstado(), minutos), pagado[1], TOPE_XP_DIA);
        }
        boolean tope = pagado[0] + monedas >= TOPE_MONEDAS_DIA;

        if (monedas > 0) {
            usuarioMonedaService.registrarMovimiento(usuario, monedas, ORIGEN_MONEDAS, sesion.getId(),
                    "Sesión de Norday Focus");
        }
        if (xp > 0) {
            // Concede también los logros de fase de Nori si toca.
            mascotaService.ganarExperiencia(usuarioId, xp);
        }
        logrosEnfoqueService.evaluar(usuario, sesion, tope);

        List<String> logros = new ArrayList<>();
        for (String codigo : codigosDeLogros(usuario)) {
            if (!antes.contains(codigo)) logros.add(codigo);
        }
        sesionDAO.guardarPremio(sesion.getId(), monedas, xp, SesionEnfoque.textoDeLogros(logros), tope);
        return new Premio(monedas, xp, List.copyOf(logros), tope);
    }

    /** El premio tal como quedó guardado en la sesión (reintentos). */
    public static Premio guardado(SesionEnfoque sesion) {
        return new Premio(sesion.getPremioMonedas(), sesion.getPremioXp(), sesion.logrosDelPremio(),
                sesion.isPremioTope());
    }

    /** Lo que se puede pagar de {@code quiere} con {@code yaPagado} de un {@code tope}: nunca negativo. */
    static int recortar(int quiere, int yaPagado, int tope) {
        return Math.max(0, Math.min(quiere, tope - yaPagado));
    }

    /** Los códigos de los logros del usuario, en el orden en que los tiene. */
    private Set<String> codigosDeLogros(Usuario usuario) {
        return logroService.logrosDeUsuario(usuario).stream()
                .map(UsuarioLogro::getLogro)
                .map(logro -> logro.getCodigo())
                .collect(Collectors.toCollection(java.util.LinkedHashSet::new));
    }
}
