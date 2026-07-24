package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Frecuencia;
import com.joaquim.habitosapp.model.Habito;
import com.joaquim.habitosapp.model.Racha;
import com.joaquim.habitosapp.repository.IHabitoDAO;
import com.joaquim.habitosapp.repository.IRachaDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

@Component
public class RachaSchedulerService {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(RachaSchedulerService.class);

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Scheduled(cron = "0 5 0 * * *", zone = "Europe/Madrid")
    public void evaluarCierreDePeriodos() {
        boolean hoyEsLunes = LocalDate.now().getDayOfWeek() == DayOfWeek.MONDAY;
        List<Habito> habitos = habitoDAO.findTodosActivos();

        int rachasRotas = 0;
        int periodosReseteados = 0;

        for (Habito habito : habitos) {
            boolean corresponde =
                    habito.getFrecuencia() == Frecuencia.DIARIO ||
                            (habito.getFrecuencia() == Frecuencia.SEMANAL && hoyEsLunes);

            if (!corresponde) continue;

            Racha racha = rachaDAO.findByHabito(habito);
            if (racha == null) continue;

            if (!racha.isMetaAlcanzadaPeriodoActual()) {
                racha.setRachaActual(0);
                rachasRotas++;
            }

            racha.setMetaAlcanzadaPeriodoActual(false);
            rachaDAO.update(racha);
            periodosReseteados++;
        }

        log.info("RachaScheduler: {} periodos evaluados, {} rachas rotas.", periodosReseteados, rachasRotas);
    }
}