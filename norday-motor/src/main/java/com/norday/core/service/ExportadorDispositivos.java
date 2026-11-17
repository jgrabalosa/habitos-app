package com.norday.core.service;

import com.norday.core.model.Usuario;
import com.norday.core.model.UsuarioDispositivo;
import com.norday.core.repository.IUsuarioDispositivoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aporta a la exportación RGPD en qué apps tiene el usuario las
 * notificaciones registradas y desde cuándo.
 *
 * Sin el token, por la misma regla que ExportacionDatosService aplica a
 * usuario.fcm_token: es un identificador de dispositivo que no aporta nada al
 * titular y sí riesgo si el archivo acaba donde no debe.
 */
@Component
public class ExportadorDispositivos implements ExportadorDatosUsuario {

    @Autowired
    private IUsuarioDispositivoDAO dispositivoDAO;

    @Override
    public String seccion() {
        return "notificaciones";
    }

    @Override
    public Map<String, Object> exportar(Usuario usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("apps", dispositivoDAO.findByUsuario(usuario.getUsuarioId()).stream()
                .map(this::datosDispositivo)
                .collect(Collectors.toList()));
        return datos;
    }

    private Map<String, Object> datosDispositivo(UsuarioDispositivo dispositivo) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("app", dispositivo.getAppId());
        mapa.put("actualizadoEn", ExportacionDatosService.texto(dispositivo.getActualizadoEn()));
        return mapa;
    }
}
