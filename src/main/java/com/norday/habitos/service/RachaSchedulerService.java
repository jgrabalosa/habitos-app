package com.norday.habitos.service;

import org.springframework.stereotype.Component;

/**
 * Ya no decide nada sobre las rachas.
 *
 * Hasta ahora ponía rachaActual a 0 y limpiaba el flag de periodo a las
 * 00:05 de Madrid. Eso solo funcionaba con una única medianoche: con
 * usuarios en varias zonas no hay un instante en el que "cierra el periodo"
 * para todos, y si el barrido no corría (despliegue, caída, cold start) la
 * racha se corrompía o se congelaba.
 *
 * La rotura es ahora perezosa y vive en el modelo: Racha guarda el periodo
 * en que se alcanzó la meta y el sello se autocaduca al cambiar de periodo
 * (ver Racha.sigueViva y RachaService.rachaActualVigente). Nadie necesita
 * limpiar nada.
 *
 * La clase se conserva vacía a propósito: en el bloque siguiente recibe su
 * papel nuevo, el de avisar a quien está a punto de perder una racha, y se
 * renombra para reflejarlo.
 */
@Component
public class RachaSchedulerService {
}
