package com.norday.conocimiento.service;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.EstadoPreferenciaCategoria;
import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.conocimiento.model.dto.PreferenciaCategoriaDTO;
import com.norday.conocimiento.repository.ICategoriaConocimientoDAO;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.core.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Service
public class PreferenciaCategoriaService {

    @Autowired
    private ICategoriaConocimientoDAO categoriaDAO;

    @Autowired
    private IUsuarioCategoriaPreferenciaDAO preferenciaDAO;

    /**
     * El catálogo completo con el estado de cada categoría. Una categoría sin
     * fila se devuelve como NEUTRAL: no hace falta materializar una fila para
     * decir "me da igual", que es el estado por defecto de todo.
     */
    public List<PreferenciaCategoriaDTO> obtenerPreferencias(Usuario usuario) {
        Map<Integer, EstadoPreferenciaCategoria> estadosGuardados = new HashMap<>();
        for (UsuarioCategoriaPreferencia preferencia : preferenciaDAO.findByUsuario(usuario)) {
            estadosGuardados.put(preferencia.getCategoria().getCategoriaId(), preferencia.getEstado());
        }

        List<PreferenciaCategoriaDTO> dtos = new ArrayList<>();
        for (Categoria categoria : categoriaDAO.findAll()) {
            EstadoPreferenciaCategoria estado = estadosGuardados.getOrDefault(
                    categoria.getCategoriaId(), EstadoPreferenciaCategoria.NEUTRAL);

            PreferenciaCategoriaDTO dto = new PreferenciaCategoriaDTO();
            dto.setCategoriaId(categoria.getCategoriaId());
            dto.setNombre(categoria.getNombre());
            dto.setIcono(categoria.getIcono());
            dto.setColor(categoria.getColor());
            dto.setEstado(estado.name());
            dtos.add(dto);
        }
        return dtos;
    }

    /**
     * Recibe el estado deseado de TODAS las categorías, no un parche.
     *
     * Va en dos pasadas, y el orden importa: primero se valida y se resuelve
     * la lista entera, y solo si todo cuadra se empieza a escribir. Cada DAO
     * es su propia transacción, así que no hay rollback que recoja los platos
     * si algo revienta a mitad del bucle — un estado inválido en la última
     * categoría dejaría guardadas las anteriores.
     *
     * Lo que se valida: que haya al menos una en QUIERE (sin eso el bucle no
     * tiene de dónde servir y la app queda vacía), que todos los estados sean
     * del enum, y que todas las categorías existan.
     */
    public void actualizarPreferencias(Usuario usuario, List<PreferenciaCategoriaDTO> nuevas) {
        if (nuevas == null || nuevas.isEmpty()) {
            throw new IllegalArgumentException("Debes elegir al menos una categoría que te interese");
        }

        // Traducir los estados y comprobar el mínimo es gratis, así que va
        // primero: si el usuario no ha elegido nada, se entera de eso y no de
        // que además la categoría 7 no existe.
        List<EstadoPreferenciaCategoria> estados = new ArrayList<>();
        boolean hayAlgunaQuiere = false;
        for (PreferenciaCategoriaDTO dto : nuevas) {
            EstadoPreferenciaCategoria estado = aEstado(dto.getEstado());
            hayAlgunaQuiere |= estado == EstadoPreferenciaCategoria.QUIERE;
            estados.add(estado);
        }
        if (!hayAlgunaQuiere) {
            throw new IllegalArgumentException("Debes elegir al menos una categoría que te interese");
        }

        List<CambioPreferencia> cambios = new ArrayList<>();
        for (int i = 0; i < nuevas.size(); i++) {
            PreferenciaCategoriaDTO dto = nuevas.get(i);

            UsuarioCategoriaPreferencia existente =
                    preferenciaDAO.findByUsuarioYCategoria(usuario, dto.getCategoriaId());

            Categoria categoria = null;
            if (existente == null) {
                categoria = categoriaDAO.findById(dto.getCategoriaId());
                if (categoria == null) {
                    throw new IllegalArgumentException(
                            "La categoría " + dto.getCategoriaId() + " no existe");
                }
            }

            cambios.add(new CambioPreferencia(existente, categoria, estados.get(i)));
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (CambioPreferencia cambio : cambios) {
            if (cambio.existente != null) {
                // Solo cambia lo que el usuario ha elegido. La afinidad es
                // historial ganado interactuando: reordenar gustos no lo borra.
                cambio.existente.setEstado(cambio.estado);
                cambio.existente.setFecha(ahora);
                preferenciaDAO.update(cambio.existente);
            } else {
                preferenciaDAO.save(new UsuarioCategoriaPreferencia(
                        usuario, cambio.categoria, cambio.estado, ahora));
            }
        }
    }

    /**
     * Suma afinidad tras una señal del usuario. Si no hay fila para esa
     * categoría no hace nada: no se puede tener una señal donde no se sirven
     * píldoras, así que ese caso solo puede venir de datos incoherentes.
     */
    public void sumarAfinidad(Usuario usuario, int categoriaId, double delta) {
        UsuarioCategoriaPreferencia preferencia =
                preferenciaDAO.findByUsuarioYCategoria(usuario, categoriaId);
        if (preferencia == null) {
            return;
        }
        preferencia.setPuntuacionAfinidad(preferencia.getPuntuacionAfinidad() + delta);
        preferenciaDAO.update(preferencia);
    }

    private EstadoPreferenciaCategoria aEstado(String estado) {
        try {
            return EstadoPreferenciaCategoria.valueOf(estado);
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new IllegalArgumentException("Estado de preferencia no válido: " + estado);
        }
    }

    /** Una fila ya resuelta y validada, lista para escribir sin más consultas. */
    private static class CambioPreferencia {
        private final UsuarioCategoriaPreferencia existente; // null si hay que crearla
        private final Categoria categoria;                   // solo se usa al crear
        private final EstadoPreferenciaCategoria estado;

        CambioPreferencia(UsuarioCategoriaPreferencia existente, Categoria categoria,
                          EstadoPreferenciaCategoria estado) {
            this.existente = existente;
            this.categoria = categoria;
            this.estado = estado;
        }
    }
}
