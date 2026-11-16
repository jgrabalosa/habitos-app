package com.norday.enfoque.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.enfoque.config.LogrosEnfoqueInitializer;
import com.norday.enfoque.model.EstadoSesion;
import com.norday.enfoque.model.ParametrosPremioEnfoque;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.gamificacion.service.LogroService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.util.List;

import static com.norday.enfoque.config.LogrosEnfoqueInitializer.*;

/**
 * Los logros propios de Norday Focus (catálogo en LogrosEnfoqueInitializer).
 *
 * Se miran en el alta de una sesión que cuenta, dentro de su transacción y
 * con el usuario ya bloqueado (SesionEnfoqueService.guardar), con la sesión
 * nueva ya guardada: cuenta en lo que se mira. Se conceden con
 * LogroService.otorgarSiNoTiene, que da cada uno una sola vez y suma sus
 * monedas.
 *
 * No hay que retirarlos nunca: una sesión de Focus no se borra ni se deshace
 * (al contrario que un completado de Norday Habits). Un logro de racha no se
 * pierde al romperla.
 *
 * Todos son de estado: se miran cada vez, no solo en el instante en que se
 * cruza el umbral. Así, una sesión que llega tarde de la cola también los da.
 */
@Service
public class LogrosEnfoqueService {

    /** Cuántas sesiones seguidas sin perder ninguna pide SIN_RENDIRSE. */
    static final int SIN_RENDIRSE_SESIONES = 10;
    /** COMPLETADA en un día que pide CUATRO. */
    static final int CUATRO_SESIONES = 4;
    /** Hora local antes de la cual empieza una sesión de MANANA. */
    static final int HORA_MANANA = 8;
    /** Hora local desde la que empieza una sesión de NOCHE. */
    static final int HORA_NOCHE = 22;

    @Autowired
    private ISesionEnfoqueDAO sesionDAO;

    @Autowired
    private LogroService logroService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /**
     * Mira los logros de Focus tras guardar {@code sesion} y concede los que
     * toquen. Una sesión que no cuenta (PERDIDA) no da ninguno.
     *
     * @param topeAlcanzado si con esta sesión el día llegó al tope de monedas
     */
    public void evaluar(Usuario usuario, SesionEnfoque sesion, boolean topeAlcanzado) {
        if (!sesion.getEstado().cuenta()) return;
        int usuarioId = usuario.getUsuarioId();
        LocalDate dia = sesion.getDia();

        dar(usuario, PRIMERA_SESION, true);
        dar(usuario, CON_DESTINO, sesion.getDestinoTipo() != null);
        dar(usuario, DIA, sesionDAO.minutosDelDia(usuarioId, dia) >= ParametrosPremioEnfoque.MINUTOS_DIA_DE_ENFOQUE);
        dar(usuario, TOPE, topeAlcanzado);

        boolean completada = sesion.getEstado() == EstadoSesion.COMPLETADA;
        dar(usuario, SESION_60, completada && sesion.getDuracionObjetivoMin() >= 60);
        dar(usuario, SESION_120, completada && sesion.getDuracionObjetivoMin() >= 120);
        dar(usuario, CUATRO, completada && sesionDAO.completadasDelDia(usuarioId, dia) >= CUATRO_SESIONES);

        List<EstadoSesion> ultimas = sesionDAO.ultimosEstados(usuarioId, SIN_RENDIRSE_SESIONES);
        dar(usuario, SIN_RENDIRSE, ultimas.size() >= SIN_RENDIRSE_SESIONES
                && ultimas.stream().allMatch(EstadoSesion::cuenta));

        int hora = horaLocal(usuario, sesion);
        dar(usuario, MANANA, hora < HORA_MANANA);
        dar(usuario, NOCHE, hora >= HORA_NOCHE);

        int dias = sesionDAO.diasConSesion(usuarioId);
        for (int umbral : DIAS) {
            dar(usuario, LogrosEnfoqueInitializer.dias(umbral), dias >= umbral);
        }
        int racha = rachaHasta(usuarioId, dia);
        for (int umbral : RACHAS) {
            dar(usuario, LogrosEnfoqueInitializer.racha(umbral), racha >= umbral);
        }
        long horas = sesionDAO.minutosTotales(usuarioId) / 60;
        for (int umbral : HORAS) {
            dar(usuario, LogrosEnfoqueInitializer.horas(umbral), horas >= umbral);
        }
    }

    private void dar(Usuario usuario, String codigo, boolean merece) {
        if (merece) {
            logroService.otorgarSiNoTiene(usuario, codigo);
        }
    }

    /** La hora a la que empezó la sesión, en la zona del usuario. */
    private int horaLocal(Usuario usuario, SesionEnfoque sesion) {
        ZonedDateTime inicio = sesion.getInicioUtc().atZone(ZoneOffset.UTC)
                .withZoneSameInstant(zonaUsuarioService.zonaDe(usuario));
        return inicio.getHour();
    }

    /**
     * Días seguidos con alguna sesión que cuenta, terminando en {@code dia}
     * (el de la sesión nueva, que ya está guardada). Una sesión que llega
     * tarde de la cola mide su racha hasta su día.
     */
    int rachaHasta(int usuarioId, LocalDate dia) {
        List<LocalDate> dias = sesionDAO.diasQueCuentanHasta(usuarioId, dia);
        LocalDate esperado = dia;
        int racha = 0;
        for (LocalDate d : dias) {
            if (!d.equals(esperado)) break;
            racha++;
            esperado = esperado.minusDays(1);
        }
        return racha;
    }
}
