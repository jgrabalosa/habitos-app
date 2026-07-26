package com.norday.gamificacion.service;

import com.norday.core.model.Usuario;
import com.norday.gamificacion.model.Logro;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.repository.ILogroDAO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class LogroService {

    @Autowired
    private ILogroDAO logroDAO;

    @Autowired
    private IUsuarioLogroDAO usuarioLogroDAO;

    @Autowired
    private UsuarioMonedaService usuarioMonedaService;

    public List<Logro> catalogoActivo() {
        return logroDAO.findActivos();
    }

    public Logro buscarPorCodigo(String codigo) {
        return logroDAO.findByCodigo(codigo);
    }

    public List<UsuarioLogro> logrosDeUsuario(Usuario usuario) {
        return usuarioLogroDAO.findByUsuario(usuario);
    }

    /**
     * Otorga el logro con ese código si el usuario aún no lo tiene.
     * Devuelve true solo si se ha otorgado ahora (false si ya lo tenía o si
     * el código no existe en el catálogo).
     */
    public boolean otorgarSiNoTiene(Usuario usuario, String codigo) {
        Logro logro = buscarPorCodigo(codigo);
        if (logro != null) {
            return otorgarLogro(usuario, logro.getLogroId());
        }
        return false;
    }

    public boolean otorgarLogro(Usuario usuario, int logroId) {
        if (usuarioLogroDAO.existePorUsuarioYLogro(usuario.getUsuarioId(), logroId)) {
            return false; // Ya lo tiene, no se duplica
        }

        Logro logro = logroDAO.findById(logroId);
        if (logro == null || !logro.isActivo()) {
            return false;
        }

        UsuarioLogro usuarioLogro = new UsuarioLogro(usuario, logro);
        usuarioLogroDAO.save(usuarioLogro);

        usuarioMonedaService.registrarMovimiento(
                usuario, logro.getPuntos(), "LOGRO", logroId,
                "Logro conseguido: " + logro.getNombre()
        );

        return true;
    }


}