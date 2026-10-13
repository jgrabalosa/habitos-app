package com.norday.habitos.service;

import com.norday.core.exception.ConflictoException;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.gamificacion.service.ProductoService;
import com.norday.habitos.config.ProductosHabitosInitializer;
import com.norday.habitos.model.Habito;
import com.norday.habitos.repository.IHabitoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Pausar y reanudar un hábito.
 *
 * Vive aparte de HabitoService a propósito: pausar gasta un producto, y
 * HabitoService no puede depender de la tienda. ProductoService →
 * MascotaService → CumplimientoDiarioHabitos → HabitoService cerraría un
 * ciclo. De este servicio no depende nadie salvo el controlador.
 */
@Service
public class PausaService {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private RitmoService ritmoService;

    @Autowired
    private ProductoService productoService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    /**
     * Pausa un hábito: gasta una «Pausa» del inventario, ancla su ritmo en
     * hoy con el valor que tiene y lo deja inactivo, con lo que sale de Hoy,
     * del ritmo general y de los avisos. Todo o nada: sin unidades, o si ya
     * estaba en pausa, no se toca nada.
     */
    @Transactional
    public void pausar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        if (!habito.isActivo()) {
            throw new ConflictoException("El hábito ya está en pausa");
        }
        productoService.gastarUnidad(habito.getPropietario(), ProductosHabitosInitializer.PAUSA);
        // Antes de desactivarlo: con el hábito inactivo el ritmo ya no se pone al día.
        ritmoService.anclarAntesDeCambiar(habito);
        habito.setActivo(false);
        habitoDAO.update(habito);
    }

    /** Reanuda un hábito en pausa. Es gratis. Si ya estaba activo, no hace nada. */
    @Transactional
    public void reanudar(int id) {
        Habito habito = habitoDAO.findById(id);
        if (habito == null) {
            throw new RecursoNoEncontradoException("Hábito no encontrado");
        }
        reanudar(habito, LocalDate.now(zonaUsuarioService.zonaDe(habito.getPropietario())));
    }

    /** Igual, con el día dado, para que los tests lo fijen. */
    @Transactional
    public void reanudar(Habito habito, LocalDate hoy) {
        if (habito.isActivo()) {
            return;
        }
        ritmoService.reanudar(habito, hoy);
        habito.setActivo(true);
        habitoDAO.update(habito);
    }
}
