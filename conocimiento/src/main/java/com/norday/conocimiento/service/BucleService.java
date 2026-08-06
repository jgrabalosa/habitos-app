package com.norday.conocimiento.service;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.EstadoPreferenciaCategoria;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.model.dto.PildoraPreviewDTO;
import com.norday.conocimiento.repository.IPildoraDAO;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.core.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * El bucle principal: qué píldora se le enseña al usuario a continuación.
 *
 * La categoría se sortea con peso, no se elige la mejor. Servir siempre la
 * favorita convertiría la app en un solo tema, y el sentido de tener NEUTRALes
 * es justamente que asomen de vez en cuando.
 */
@Service
public class BucleService {

    /** Descartes seguidos tras los que una píldora deja de reaparecer para siempre. */
    private static final int MAX_DESCARTES = 3;

    /** Interacciones que tienen que pasar antes de que una descartada vuelva. */
    private static final int ESPERA_REAPARICION = 10;

    private static final double PESO_QUIERE = 3;
    private static final double PESO_NEUTRAL = 1;

    /**
     * Suelo del peso. La afinidad puede ser negativa en teoría, y sin este
     * mínimo una categoría podría quedar con peso cero y no salir jamás.
     */
    private static final double PESO_MINIMO = 0.5;

    @Autowired
    private IUsuarioCategoriaPreferenciaDAO preferenciaDAO;

    @Autowired
    private IPildoraDAO pildoraDAO;

    @Autowired
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    private final Random aleatorio = new Random();

    /** @return la siguiente píldora, o null si no queda nada que servir. */
    public PildoraPreviewDTO obtenerSiguientePildora(Usuario usuario) {
        List<CategoriaConPeso> elegibles = new ArrayList<>();
        for (UsuarioCategoriaPreferencia preferencia : preferenciaDAO.findByUsuario(usuario)) {
            if (preferencia.getEstado() == EstadoPreferenciaCategoria.NO_QUIERE) {
                continue;
            }
            double base = preferencia.getEstado() == EstadoPreferenciaCategoria.QUIERE
                    ? PESO_QUIERE : PESO_NEUTRAL;
            double peso = Math.max(base + preferencia.getPuntuacionAfinidad(), PESO_MINIMO);
            elegibles.add(new CategoriaConPeso(preferencia.getCategoria(), peso));
        }

        if (elegibles.isEmpty()) {
            return null;
        }

        // Si la categoría sorteada sale vacía se prueba la siguiente, y el
        // sorteo se repite sobre las que quedan: el peso sigue mandando en el
        // segundo intento y en el tercero, no solo en el primero.
        for (Categoria categoria : ordenarPorSorteoPonderado(elegibles)) {
            List<Pildora> pool = pildoraDAO.findActivasElegibles(usuario, categoria.getCategoriaId());
            if (pool.isEmpty()) {
                continue;
            }

            List<Pildora> poolFinal = new ArrayList<>();
            for (Pildora pildora : pool) {
                if (puedeServirse(usuario, pildora)) {
                    poolFinal.add(pildora);
                }
            }

            if (!poolFinal.isEmpty()) {
                return aPreviewDTO(poolFinal.get(aleatorio.nextInt(poolFinal.size())));
            }
        }

        return null;
    }

    /**
     * Una píldora nunca vista se sirve siempre. Una descartada vuelve solo si
     * no ha agotado sus tres oportunidades y si el usuario ha seguido leyendo
     * lo suficiente como para haber olvidado que la apartó.
     */
    private boolean puedeServirse(Usuario usuario, Pildora pildora) {
        UsuarioPildora interaccion =
                usuarioPildoraDAO.findByUsuarioYPildora(usuario, pildora.getPildoraId());

        if (interaccion == null) {
            return true;
        }
        // Filtro defensivo: lo leído ya lo excluye la consulta del DAO.
        if (interaccion.getEstado() != EstadoPildora.DESCARTADA) {
            return false;
        }
        if (interaccion.getNumDescartes() >= MAX_DESCARTES) {
            return false;
        }
        return usuarioPildoraDAO.contarInteraccionesDesde(
                usuario, interaccion.getFechaUltimaInteraccion()) >= ESPERA_REAPARICION;
    }

    /**
     * Orden completo de intento. En cada paso sortea entre las categorías que
     * aún no ha colocado, así que el peso influye en toda la secuencia y no
     * solo en la cabeza de la lista.
     */
    private List<Categoria> ordenarPorSorteoPonderado(List<CategoriaConPeso> candidatas) {
        List<CategoriaConPeso> restantes = new ArrayList<>(candidatas);
        List<Categoria> orden = new ArrayList<>();

        while (!restantes.isEmpty()) {
            double total = 0;
            for (CategoriaConPeso candidata : restantes) {
                total += candidata.peso;
            }

            double corte = aleatorio.nextDouble() * total;
            int elegida = restantes.size() - 1; // caída por si el redondeo se pasa de largo
            for (int i = 0; i < restantes.size(); i++) {
                corte -= restantes.get(i).peso;
                if (corte <= 0) {
                    elegida = i;
                    break;
                }
            }

            orden.add(restantes.remove(elegida).categoria);
        }
        return orden;
    }

    private PildoraPreviewDTO aPreviewDTO(Pildora pildora) {
        PildoraPreviewDTO dto = new PildoraPreviewDTO();
        dto.setPildoraId(pildora.getPildoraId());
        dto.setPreviewCorto(pildora.getPreviewCorto());
        dto.setImagenUrl(pildora.getImagenUrl());
        return dto;
    }

    private static class CategoriaConPeso {
        private final Categoria categoria;
        private final double peso;

        CategoriaConPeso(Categoria categoria, double peso) {
            this.categoria = categoria;
            this.peso = peso;
        }
    }
}
