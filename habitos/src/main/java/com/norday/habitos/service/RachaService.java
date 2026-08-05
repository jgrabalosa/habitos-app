package com.norday.habitos.service;

import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.repository.IRachaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.ZoneId;

/**
 * Única puerta de lectura de una racha.
 *
 * La rotura es perezosa: no hay ningún cron que ponga rachas a cero. Cuando
 * alguien pregunta por la racha y el sello de periodo revela que está muerta,
 * se normaliza aquí — devolviendo 0 y persistiéndolo.
 *
 * Se persiste a propósito: si solo se calculase al vuelo, el valor en BD
 * quedaría inflado para siempre y cualquier lectura que no pase por aquí
 * (analítica, SQL manual, un futuro ranking) mentiría. rachaMaxima no se
 * toca nunca: ya era correcta.
 */
@Service
public class RachaService {

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    public ZoneId zonaDe(Habito habito) {
        return zonaUsuarioService.zonaDe(habito != null ? habito.getPropietario() : null);
    }

    /**
     * Racha actual real, normalizando en BD si estaba muerta.
     * Ocurre una sola vez por racha rota.
     */
    public int rachaActualVigente(Racha racha) {
        if (racha == null) {
            return 0;
        }
        if (racha.getRachaActual() == 0) {
            return 0; // ya está a cero, no hay nada que normalizar
        }
        if (racha.sigueViva(zonaDe(racha.getHabito()))) {
            return racha.getRachaActual();
        }
        racha.setRachaActual(0);
        rachaDAO.update(racha);
        return 0;
    }

    /** Igual que el anterior, pero partiendo del hábito. */
    public int rachaActualVigente(Habito habito) {
        return rachaActualVigente(rachaDAO.findByHabito(habito));
    }
}
