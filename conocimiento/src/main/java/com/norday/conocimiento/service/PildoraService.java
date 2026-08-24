package com.norday.conocimiento.service;

import com.norday.conocimiento.model.Categoria;
import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.PildoraCategoria;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.model.ValoracionPildora;
import com.norday.conocimiento.model.dto.PildoraColeccionItemDTO;
import com.norday.conocimiento.model.dto.PildoraDetalleDTO;
import com.norday.conocimiento.repository.IPildoraDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.conocimiento.repository.IValoracionPildoraDAO;
import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class PildoraService {

    /** Afinidad que da leer una píldora por primera vez. */
    private static final double AFINIDAD_MATCH = 1;

    /** Guardar es la señal fuerte: el usuario dice que quiere volver a esto. */
    private static final double AFINIDAD_GUARDAR = 5;

    @Autowired
    private IPildoraDAO pildoraDAO;

    @Autowired
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    @Autowired
    private IValoracionPildoraDAO valoracionDAO;

    @Autowired
    private PreferenciaCategoriaService preferenciaCategoriaService;

    /**
     * El usuario destapa una píldora. Es el único punto donde viaja el
     * contenido completo.
     */
    public PildoraDetalleDTO match(Usuario usuario, int pildoraId) {
        Pildora pildora = pildoraDAO.findById(pildoraId);
        if (pildora == null || !pildora.isActiva()) {
            throw new RecursoNoEncontradoException("Píldora no encontrada");
        }

        LocalDateTime ahora = LocalDateTime.now();
        UsuarioPildora interaccion = usuarioPildoraDAO.findByUsuarioYPildora(usuario, pildoraId);

        // Solo la primera lectura suma: volver a abrir algo ya leído no es una
        // señal nueva sobre lo que le interesa al usuario.
        boolean esPrimeraLectura = interaccion == null
                || interaccion.getEstado() == EstadoPildora.DESCARTADA;

        if (interaccion == null) {
            interaccion = new UsuarioPildora(usuario, pildora, EstadoPildora.VISTA, 0, ahora);
            usuarioPildoraDAO.save(interaccion);
        } else {
            if (interaccion.getEstado() == EstadoPildora.DESCARTADA) {
                interaccion.setEstado(EstadoPildora.VISTA);
            }
            interaccion.setFechaUltimaInteraccion(ahora);
            usuarioPildoraDAO.update(interaccion);
        }

        if (esPrimeraLectura) {
            sumarAfinidadCategoriaPrincipal(usuario, pildoraId, AFINIDAD_MATCH);
        }

        return aDetalleDTO(usuario, pildora, interaccion.getEstado());
    }

    /**
     * El usuario aparta una píldora sin leerla. No toca la afinidad: es una
     * señal demasiado débil — puede no apetecerle ahora y sí mañana.
     */
    public void descartar(Usuario usuario, int pildoraId) {
        Pildora pildora = pildoraDAO.findById(pildoraId);
        if (pildora == null || !pildora.isActiva()) {
            throw new RecursoNoEncontradoException("Píldora no encontrada");
        }

        LocalDateTime ahora = LocalDateTime.now();
        UsuarioPildora interaccion = usuarioPildoraDAO.findByUsuarioYPildora(usuario, pildoraId);

        if (interaccion == null) {
            usuarioPildoraDAO.save(
                    new UsuarioPildora(usuario, pildora, EstadoPildora.DESCARTADA, 1, ahora));
            return;
        }

        // Lo ya leído no se "des-lee". Silencio en vez de excepción: el cliente
        // puede mandar un descarte tardío por una pulsación doble, y eso no es
        // un error que merezca romperle la pantalla.
        if (interaccion.getEstado() != EstadoPildora.DESCARTADA) {
            return;
        }

        interaccion.setNumDescartes(interaccion.getNumDescartes() + 1);
        interaccion.setFechaUltimaInteraccion(ahora);
        usuarioPildoraDAO.update(interaccion);
    }

    /**
     * Marca o desmarca como guardada. Desmarcar no resta afinidad: lo ganado
     * no se retira, solo deja de sumar.
     */
    public void guardar(Usuario usuario, int pildoraId, boolean guardar) {
        UsuarioPildora interaccion = usuarioPildoraDAO.findByUsuarioYPildora(usuario, pildoraId);
        if (interaccion == null || interaccion.getEstado() == EstadoPildora.DESCARTADA) {
            throw new IllegalArgumentException("No puedes guardar una píldora que no has leído");
        }

        boolean estabaGuardada = interaccion.getEstado() == EstadoPildora.GUARDADA;

        interaccion.setEstado(guardar ? EstadoPildora.GUARDADA : EstadoPildora.VISTA);
        interaccion.setFechaUltimaInteraccion(LocalDateTime.now());
        usuarioPildoraDAO.update(interaccion);

        if (guardar && !estabaGuardada) {
            sumarAfinidadCategoriaPrincipal(usuario, pildoraId, AFINIDAD_GUARDAR);
        }
    }

    public List<PildoraColeccionItemDTO> obtenerColeccion(Usuario usuario, Integer categoriaId, String estado) {
        EstadoPildora filtroEstado = null;
        if (estado != null && !estado.isBlank()) {
            try {
                filtroEstado = EstadoPildora.valueOf(estado);
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Estado no válido: " + estado);
            }
            if (filtroEstado == EstadoPildora.DESCARTADA) {
                throw new IllegalArgumentException("Lo descartado no forma parte de tu colección");
            }
        }

        List<PildoraColeccionItemDTO> items = new ArrayList<>();
        for (UsuarioPildora interaccion : usuarioPildoraDAO.findParaColeccion(usuario, categoriaId, filtroEstado)) {
            Pildora pildora = interaccion.getPildora();

            PildoraColeccionItemDTO item = new PildoraColeccionItemDTO();
            item.setPildoraId(pildora.getPildoraId());
            item.setTitulo(pildora.getTitulo());
            item.setImagenUrl(pildora.getImagenUrl());
            item.setEstado(interaccion.getEstado().name());

            PildoraCategoria principal = pildoraDAO.findCategoriaPrincipal(pildora.getPildoraId());
            item.setCategoriaPrincipal(principal != null ? principal.getCategoria().getNombre() : null);

            items.add(item);
        }
        return items;
    }

    private void sumarAfinidadCategoriaPrincipal(Usuario usuario, int pildoraId, double delta) {
        PildoraCategoria principal = pildoraDAO.findCategoriaPrincipal(pildoraId);
        if (principal == null) {
            return; // píldora sin principal marcada: no hay a quién sumarle
        }
        preferenciaCategoriaService.sumarAfinidad(
                usuario, principal.getCategoria().getCategoriaId(), delta);
    }

    private PildoraDetalleDTO aDetalleDTO(Usuario usuario, Pildora pildora, EstadoPildora estado) {
        PildoraDetalleDTO dto = new PildoraDetalleDTO();
        dto.setPildoraId(pildora.getPildoraId());
        dto.setTitulo(pildora.getTitulo());
        dto.setContenidoCompleto(pildora.getContenidoCompleto());
        dto.setLibroOrigen(pildora.getLibroOrigen());
        dto.setImagenUrl(pildora.getImagenUrl());
        dto.setEstado(estado.name());

        List<String> nombres = new ArrayList<>();
        for (Categoria categoria : pildoraDAO.findCategorias(pildora.getPildoraId())) {
            nombres.add(categoria.getNombre());
        }
        dto.setCategorias(nombres);

        ValoracionPildora valoracion =
                valoracionDAO.findByUsuarioYPildora(usuario, pildora.getPildoraId());
        dto.setValoracionUsuario(valoracion != null ? valoracion.getPuntuacion() : null);
        // La nota vuelve al cliente para que pueda precargarla al reabrir el
        // diálogo de valoración: `valorar()` sobrescribe con lo que reciba, así
        // que sin esto cambiar solo la puntuación borraría la nota ya escrita.
        dto.setNotaPersonalUsuario(valoracion != null ? valoracion.getNotaPersonal() : null);

        return dto;
    }
}
