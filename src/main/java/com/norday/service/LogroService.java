package com.norday.service;

import com.norday.model.Logro;
import com.norday.model.Usuario;
import com.norday.model.UsuarioLogro;
import com.norday.repository.ILogroDAO;
import com.norday.repository.IUsuarioLogroDAO;
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