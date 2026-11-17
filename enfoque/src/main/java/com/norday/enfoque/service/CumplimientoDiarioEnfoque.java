package com.norday.enfoque.service;

import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.enfoque.model.ParametrosPremioEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import com.norday.gamificacion.model.AvanceDeHoy;
import com.norday.gamificacion.service.CumplimientoDiarioPort;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

import static com.norday.enfoque.model.ParametrosPremioEnfoque.DIAS_EN_USO;
import static com.norday.enfoque.model.ParametrosPremioEnfoque.DIAS_SIN_SESION_PARA_AUSENTE;

/**
 * Cómo va hoy el usuario en Norday Focus, para el ánimo de Nori (16-nov-2026).
 * El motor junta esta respuesta con la de las otras apps
 * (CumplimientoCombinado). Solo cuentan las sesiones que cuentan (COMPLETADA
 * y TERMINADA).
 *
 * <ul>
 *   <li>En uso: alguna sesión en los últimos 14 días.</li>
 *   <li>COMPLETO: un «día de enfoque», 50 minutos o más hoy.</li>
 *   <li>EMPEZADO: alguna sesión hoy.</li>
 *   <li>AUSENTE: 3 días seguidos o más sin ninguna antes de hoy, contando
 *       desde la última sesión o, si no hay, desde el alta (el recién llegado
 *       no está ausente). Es la misma cuenta que en Norday Habits.</li>
 *   <li>SIN_EMPEZAR: lo demás.</li>
 * </ul>
 *
 * «Hoy» es el del usuario, en su zona. No llama a registrarDiaCompleto de la
 * mascota: esa fecha la guarda y la restaura la reversión de Norday Habits, y
 * al deshacer un completado pisaría la que pusiera Focus. El ánimo y el
 * estado ya preguntan en el momento, así que no hace falta.
 */
@Component
public class CumplimientoDiarioEnfoque implements CumplimientoDiarioPort {

    @Autowired
    private ISesionEnfoqueDAO sesionDAO;

    @Autowired
    private IUsuarioDAO usuarioDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Autowired
    private RelojEnfoque reloj;

    @Override
    public boolean enUso(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return false;
        return sesionDAO.hayQueCuentaDesde(usuarioId, hoy(usuario).minusDays(DIAS_EN_USO - 1));
    }

    @Override
    public boolean hoyCumplido(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return false;
        return sesionDAO.minutosDelDia(usuarioId, hoy(usuario)) >= ParametrosPremioEnfoque.MINUTOS_DIA_DE_ENFOQUE;
    }

    @Override
    public AvanceDeHoy comoVaHoy(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) return AvanceDeHoy.SIN_EMPEZAR;
        ZoneId zona = zonaUsuarioService.zonaDe(usuario);
        LocalDate hoy = LocalDate.ofInstant(reloj.ahora(), zona);
        int minutosHoy = sesionDAO.minutosDelDia(usuarioId, hoy);
        if (minutosHoy >= ParametrosPremioEnfoque.MINUTOS_DIA_DE_ENFOQUE) return AvanceDeHoy.COMPLETO;
        if (minutosHoy > 0) return AvanceDeHoy.EMPEZADO;

        LocalDate referencia = sesionDAO.ultimoDiaQueCuentaAntesDe(usuarioId, hoy);
        if (usuario.getFechaRegistro() != null) {
            // fechaRegistro se guarda en UTC; el día es el del usuario.
            LocalDate alta = usuario.getFechaRegistro().atZone(ZoneOffset.UTC).withZoneSameInstant(zona).toLocalDate();
            if (referencia == null || alta.isAfter(referencia)) referencia = alta;
        }
        if (referencia == null) return AvanceDeHoy.SIN_EMPEZAR;
        long diasSin = ChronoUnit.DAYS.between(referencia, hoy) - 1;
        return diasSin >= DIAS_SIN_SESION_PARA_AUSENTE ? AvanceDeHoy.AUSENTE : AvanceDeHoy.SIN_EMPEZAR;
    }

    private LocalDate hoy(Usuario usuario) {
        return LocalDate.ofInstant(reloj.ahora(), zonaUsuarioService.zonaDe(usuario));
    }
}
