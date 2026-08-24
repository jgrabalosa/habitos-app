package com.norday.conocimiento.service;

import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.model.ValoracionPildora;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.conocimiento.repository.IValoracionPildoraDAO;
import com.norday.core.model.Usuario;
import com.norday.core.service.ExportacionDatosService;
import com.norday.core.service.ExportadorDatosUsuario;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aporta los datos de conocimiento a la exportación RGPD: estado de
 * píldoras, preferencias de categoría y valoraciones. No incluye
 * `contenidoCompleto`: es propiedad intelectual del proyecto, no dato
 * personal del usuario.
 */
@Component
public class ExportadorConocimiento implements ExportadorDatosUsuario {

    @Autowired
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    @Autowired
    private IUsuarioCategoriaPreferenciaDAO usuarioCategoriaPreferenciaDAO;

    @Autowired
    private IValoracionPildoraDAO valoracionPildoraDAO;

    @Override
    public String seccion() {
        return "conocimiento";
    }

    @Override
    public Map<String, Object> exportar(Usuario usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("pildoras", usuarioPildoraDAO.findByUsuario(usuario).stream()
                .map(this::datosPildora)
                .collect(Collectors.toList()));
        datos.put("preferenciasCategorias", usuarioCategoriaPreferenciaDAO.findByUsuario(usuario).stream()
                .map(this::datosPreferencia)
                .collect(Collectors.toList()));
        datos.put("valoraciones", valoracionPildoraDAO.findByUsuario(usuario).stream()
                .map(this::datosValoracion)
                .collect(Collectors.toList()));
        return datos;
    }

    private Map<String, Object> datosPildora(UsuarioPildora usuarioPildora) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("titulo", usuarioPildora.getPildora().getTitulo());
        mapa.put("estado", usuarioPildora.getEstado().name());
        mapa.put("numDescartes", usuarioPildora.getNumDescartes());
        mapa.put("fechaUltimaInteraccion", ExportacionDatosService.texto(usuarioPildora.getFechaUltimaInteraccion()));
        return mapa;
    }

    private Map<String, Object> datosPreferencia(UsuarioCategoriaPreferencia preferencia) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("nombre", preferencia.getCategoria().getNombre());
        mapa.put("estado", preferencia.getEstado().name());
        mapa.put("puntuacionAfinidad", preferencia.getPuntuacionAfinidad());
        mapa.put("fecha", ExportacionDatosService.texto(preferencia.getFecha()));
        return mapa;
    }

    private Map<String, Object> datosValoracion(ValoracionPildora valoracion) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("titulo", valoracion.getPildora().getTitulo());
        mapa.put("puntuacion", valoracion.getPuntuacion());
        mapa.put("notaPersonal", valoracion.getNotaPersonal());
        mapa.put("fecha", ExportacionDatosService.texto(valoracion.getFecha()));
        return mapa;
    }
}
