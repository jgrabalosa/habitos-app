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
import java.util.List;

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

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private HabitoService habitoService;

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
        // Como completar y editar: de uno en uno por usuario, con el hábito
        // releído. Gastar la unidad es leer la cantidad y escribirla menos
        // uno; sin el bloqueo, dos pausas a la vez podían leer las dos la
        // misma cantidad y salir bien las dos gastando una sola unidad (dos
        // hábitos pausados con una «Pausa»), o gastar dos en el mismo hábito.
        // Con él, la segunda espera: si es el mismo hábito lo ve ya en pausa
        // (409, sin gastar), y si es otro lee la cantidad que dejó la
        // primera. Y un completado o una edición que ya habían leído el
        // hábito activo ven la pausa.
        habito = habitoService.bloquear(habito);
        if (!habito.isActivo()) {
            throw new ConflictoException("El hábito ya está en pausa");
        }
        productoService.gastarUnidad(habito.getPropietario(), ProductosHabitosInitializer.PAUSA);
        // Si ha llegado un cambio de objetivo programado, se aplica antes de
        // anclar: el ritmo de hasta hoy se calcula con el objetivo de cada día.
        objetivoService.ponerAlDia(List.of(habito),
                LocalDate.now(zonaUsuarioService.zonaDe(habito.getPropietario())));
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
        // Como pausar: de uno en uno y con el hábito releído.
        habito = habitoService.bloquear(habito);
        if (habito.isActivo()) {
            return;
        }
        // Un cambio programado que llegó durante la pausa se aplica antes de
        // volver; en pausa no ancla el ritmo, que pasa ahora al día de la vuelta.
        objetivoService.ponerAlDia(List.of(habito), hoy);
        ritmoService.reanudar(habito, hoy);
        habito.setActivo(true);
        habitoDAO.update(habito);
    }
}
