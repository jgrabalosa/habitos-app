package com.norday.habitos;

import com.norday.habitos.service.NotificadorRachaEnPeligro;
import org.junit.jupiter.api.Test;
import org.springframework.scheduling.annotation.Scheduled;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Lo que el rediseño de rachas apaga tiene que seguir apagado. El código se
 * conserva, así que basta una anotación para volver a encenderlo sin querer.
 */
class AvisosApagadosTest {

    @Test
    void elAvisoDeRachaEnPeligro_noEstaProgramado() throws NoSuchMethodException {
        assertNull(NotificadorRachaEnPeligro.class
                        .getMethod("avisarRachasEnPeligro")
                        .getAnnotation(Scheduled.class),
                "El barrido de las 21:00 no debe llevar @Scheduled");
    }
}
