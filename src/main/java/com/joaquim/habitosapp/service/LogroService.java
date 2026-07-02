package com.joaquim.habitosapp.service;

import com.joaquim.habitosapp.model.Logro;
import com.joaquim.habitosapp.model.Usuario;
import com.joaquim.habitosapp.model.UsuarioLogro;
import com.joaquim.habitosapp.repository.ILogroDAO;
import com.joaquim.habitosapp.repository.IUsuarioLogroDAO;
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