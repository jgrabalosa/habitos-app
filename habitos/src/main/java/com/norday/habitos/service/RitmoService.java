package com.norday.habitos.service;

import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.CalculoRitmo;
import com.norday.habitos.model.CalculoRitmo.RitmoAlDia;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

/**
 * Única puerta de lectura del ritmo de un hábito.
 *
 * El ritmo no se guarda al día: se guarda un ancla (ver {@link Ritmo}) y aquí
 * se pone al día repasando los registros. No hay proceso nocturno. Completar
 * o deshacer un hábito no pasa por aquí ni escribe en la tabla: el cálculo
 * sale solo de los registros que haya.
 *
 * Lo único que se guarda es el avance del ancla al empezar una semana nueva,
 * y se guarda a propósito: lo anterior al lunes ya no puede cambiar, así que
 * no tiene sentido repasarlo en cada lectura.
 */
@Service
public class RitmoService {

    @Autowired
    private IRitmoDAO ritmoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /** El ritmo de hoy de un hábito, de 0 a 1, en el "hoy" de su dueño. */
    @Transactional
    public double ritmoVigente(Habito habito) {
        return ritmoVigente(habito, LocalDate.now(zonaUsuarioService.zonaDe(habito.getPropietario())));
    }

    /**
     * Igual, con el día dado. Existe para que los tests fijen el día: contra
     * el reloj real no se puede escribir un test de cambio de semana.
     */
    @Transactional
    public double ritmoVigente(Habito habito, LocalDate hoy) {
        Ritmo ritmo = ritmoDAO.findByHabito(habito);
        if (ritmo == null) {
            // No debería pasar: el ritmo nace con el hábito y la migración se
            // lo dio a los que ya existían. Si falta, empieza hoy.
            ritmo = new Ritmo(habito, hoy);
            ritmoDAO.save(ritmo);
            return ritmo.getValor();
        }

        // Un semanal cuenta la semana entera, así que se lee desde el inicio
        // del periodo del ancla. Hoy no se lee: su periodo sigue abierto.
        LocalDate inicio = habito.getFrecuencia().rangoPeriodo(ritmo.getDesde())[0];
        List<LocalDate> fechasHechas = inicio.isBefore(hoy)
                ? registroDAO.findByHabitoAndRango(habito, inicio, hoy.minusDays(1)).stream()
                        .filter(Registro::isCompletado)
                        .map(Registro::getFecha)
                        .toList()
                : List.of();

        RitmoAlDia alDia = CalculoRitmo.alDia(ritmo.getValor(), ritmo.getDesde(),
                habito.getFrecuencia(), habito.getMeta(), habito.getFechaInicio(), fechasHechas, hoy);

        if (alDia.desdeAncla().isAfter(ritmo.getDesde())) {
            ritmo.setValor(alDia.valorAncla());
            ritmo.setDesde(alDia.desdeAncla());
            ritmoDAO.update(ritmo);
        }
        return alDia.valor();
    }
}
