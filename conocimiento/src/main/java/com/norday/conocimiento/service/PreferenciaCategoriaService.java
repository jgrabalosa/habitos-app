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
     * La validación va entera antes de tocar nada: si el usuario se queda sin
     * ninguna categoría en QUIERE, el bucle no tendría de dónde servir y la app
     * quedaría vacía. Mejor rechazar la petición que dejarla a medias.
     */
    public void actualizarPreferencias(Usuario usuario, List<PreferenciaCategoriaDTO> nuevas) {
        if (nuevas == null || nuevas.isEmpty()) {
            throw new IllegalArgumentException("Debes elegir al menos una categoría que te interese");
        }

        boolean hayAlgunaQuiere = false;
        for (PreferenciaCategoriaDTO dto : nuevas) {
            if (EstadoPreferenciaCategoria.QUIERE.name().equals(dto.getEstado())) {
                hayAlgunaQuiere = true;
                break;
            }
        }
        if (!hayAlgunaQuiere) {
            throw new IllegalArgumentException("Debes elegir al menos una categoría que te interese");
        }

        LocalDateTime ahora = LocalDateTime.now();
        for (PreferenciaCategoriaDTO dto : nuevas) {
            EstadoPreferenciaCategoria estado = aEstado(dto.getEstado());

            UsuarioCategoriaPreferencia existente =
                    preferenciaDAO.findByUsuarioYCategoria(usuario, dto.getCategoriaId());

            if (existente != null) {
                // Solo cambia lo que el usuario ha elegido. La afinidad es
                // historial ganado interactuando: reordenar gustos no lo borra.
                existente.setEstado(estado);
                existente.setFecha(ahora);
                preferenciaDAO.update(existente);
            } else {
                Categoria categoria = categoriaDAO.findById(dto.getCategoriaId());
                if (categoria == null) {
                    throw new IllegalArgumentException(
                            "La categoría " + dto.getCategoriaId() + " no existe");
                }
                preferenciaDAO.save(new UsuarioCategoriaPreferencia(usuario, categoria, estado, ahora));
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
}
