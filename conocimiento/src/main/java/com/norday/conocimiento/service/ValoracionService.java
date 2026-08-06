package com.norday.conocimiento.service;

import com.norday.conocimiento.model.EstadoPildora;
import com.norday.conocimiento.model.Pildora;
import com.norday.conocimiento.model.PildoraCategoria;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.model.ValoracionPildora;
import com.norday.conocimiento.repository.IPildoraDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.conocimiento.repository.IValoracionPildoraDAO;
import com.norday.core.model.Usuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;

@Service
public class ValoracionService {

    /** Tres estrellas es el punto neutro: ni suma ni resta. */
    private static final int PUNTUACION_NEUTRA = 3;

    /** Cada estrella por encima o por debajo de la neutra pesa esto. */
    private static final double PESO_ESTRELLA = 2;

    @Autowired
    private IPildoraDAO pildoraDAO;

    @Autowired
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    @Autowired
    private IValoracionPildoraDAO valoracionDAO;

    @Autowired
    private PreferenciaCategoriaService preferenciaCategoriaService;

    /**
     * Valorar es reversible: el usuario puede cambiar de opinión. Por eso la
     * afinidad se ajusta por la diferencia con lo que ya había aplicado, no
     * sumando el nuevo ajuste encima del viejo — si no, votar cinco estrellas
     * tres veces seguidas valdría el triple que votarlas una vez.
     */
    public void valorar(Usuario usuario, int pildoraId, int puntuacion, String notaPersonal) {
        if (puntuacion < 1 || puntuacion > 5) {
            throw new IllegalArgumentException("La puntuación debe estar entre 1 y 5");
        }

        UsuarioPildora interaccion = usuarioPildoraDAO.findByUsuarioYPildora(usuario, pildoraId);
        if (interaccion == null || interaccion.getEstado() == EstadoPildora.DESCARTADA) {
            throw new IllegalArgumentException("No puedes valorar una píldora que no has leído");
        }

        LocalDateTime ahora = LocalDateTime.now();
        ValoracionPildora valoracion = valoracionDAO.findByUsuarioYPildora(usuario, pildoraId);

        double ajusteNuevo = (puntuacion - PUNTUACION_NEUTRA) * PESO_ESTRELLA;
        double ajusteViejo = 0;

        if (valoracion == null) {
            Pildora pildora = pildoraDAO.findById(pildoraId);
            valoracion = new ValoracionPildora(usuario, pildora, puntuacion, notaPersonal, ahora);
            valoracionDAO.save(valoracion);
        } else {
            ajusteViejo = (valoracion.getPuntuacion() - PUNTUACION_NEUTRA) * PESO_ESTRELLA;
            valoracion.setPuntuacion(puntuacion);
            valoracion.setNotaPersonal(notaPersonal);
            valoracion.setFecha(ahora);
            valoracionDAO.update(valoracion);
        }

        double delta = ajusteNuevo - ajusteViejo;
        if (delta != 0) {
            PildoraCategoria principal = pildoraDAO.findCategoriaPrincipal(pildoraId);
            if (principal != null) {
                preferenciaCategoriaService.sumarAfinidad(
                        usuario, principal.getCategoria().getCategoriaId(), delta);
            }
        }
    }
}
