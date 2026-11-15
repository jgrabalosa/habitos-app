package com.norday.enfoque.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ExportacionDatosService;
import com.norday.core.service.ExportadorDatosUsuario;
import com.norday.enfoque.model.SesionEnfoque;
import com.norday.enfoque.repository.ISesionEnfoqueDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aporta a la exportación RGPD las sesiones de Norday Focus del usuario, de la
 * más antigua a la más reciente.
 *
 * Sin la clave: es un detalle del protocolo entre la app y el servidor, no un
 * dato del usuario (la misma regla que registro.clave_cliente).
 */
@Component
public class ExportadorEnfoque implements ExportadorDatosUsuario {

    @Autowired
    private ISesionEnfoqueDAO sesionDAO;

    @Override
    public String seccion() {
        return "enfoque";
    }

    @Override
    public Map<String, Object> exportar(Usuario usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("sesiones", sesionDAO.findByUsuario(usuario.getUsuarioId()).stream()
                .map(this::datosSesion)
                .collect(Collectors.toList()));
        return datos;
    }

    private Map<String, Object> datosSesion(SesionEnfoque sesion) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("inicioUtc", ExportacionDatosService.texto(sesion.getInicioUtc()));
        mapa.put("duracionSeg", sesion.getDuracionSeg());
        mapa.put("duracionObjetivoMin", sesion.getDuracionObjetivoMin());
        mapa.put("estado", sesion.getEstado().name());
        mapa.put("dia", ExportacionDatosService.texto(sesion.getDia()));
        mapa.put("minutos", sesion.minutos());
        mapa.put("destinoTipo", sesion.getDestinoTipo());
        mapa.put("destinoId", sesion.getDestinoRef());
        mapa.put("aporteEstado", sesion.getAporteEstado().name());
        mapa.put("aporteMotivo", sesion.getAporteMotivo());
        mapa.put("minutosAportados", sesion.getMinutosAportados());
        mapa.put("creadoEn", ExportacionDatosService.texto(sesion.getCreadoEn()));
        return mapa;
    }
}
