package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ExportacionDatosService;
import com.norday.core.service.ExportadorDatosUsuario;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aporta los datos del dominio "hábitos" a la exportación RGPD: hábitos,
 * rachas, registros y categorías personalizadas del usuario.
 */
@Component
public class ExportadorHabitos implements ExportadorDatosUsuario {

    @Autowired
    private IHabitoDAO habitoDAO;

    @Autowired
    private IRegistroDAO registroDAO;

    @Autowired
    private IRachaDAO rachaDAO;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Override
    public String seccion() {
        return "habitos";
    }

    @Override
    public Map<String, Object> exportar(Usuario usuario) {
        List<Habito> habitos = habitoDAO.findByPropietario(usuario);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("habitos", habitos.stream().map(this::datosHabito).collect(Collectors.toList()));
        datos.put("categoriasPropias", categoriaDAO.findByCreador(usuario).stream()
                .map(this::datosCategoria)
                .collect(Collectors.toList()));
        return datos;
    }

    private Map<String, Object> datosHabito(Habito habito) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("habitoId", habito.getHabitoId());
        mapa.put("nombre", habito.getNombre());
        mapa.put("descripcion", habito.getDescripcion());
        mapa.put("frecuencia", habito.getFrecuencia().name());
        mapa.put("meta", habito.getMeta());
        mapa.put("diasSemana", habito.getDiasSemana());
        mapa.put("fechaInicio", ExportacionDatosService.texto(habito.getFechaInicio()));
        mapa.put("recordatorioActivo", habito.isRecordatorioActivo());
        mapa.put("recordatorioHora", ExportacionDatosService.texto(habito.getRecordatorioHora()));
        mapa.put("activo", habito.isActivo());
        mapa.put("categoria", habito.getTipo() == null ? null : habito.getTipo().getNombre());
        mapa.put("racha", datosRacha(rachaDAO.findByHabito(habito)));
        mapa.put("registros", registroDAO.findByHabito(habito).stream()
                .map(this::datosRegistro)
                .collect(Collectors.toList()));
        return mapa;
    }

    private Map<String, Object> datosRacha(Racha racha) {
        if (racha == null) {
            return null;
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("rachaActual", racha.getRachaActual());
        mapa.put("rachaMaxima", racha.getRachaMaxima());
        mapa.put("ultimaFecha", ExportacionDatosService.texto(racha.getUltimaFecha()));
        mapa.put("periodoMetaAlcanzada", ExportacionDatosService.texto(racha.getPeriodoMetaAlcanzada()));
        return mapa;
    }

    private Map<String, Object> datosRegistro(Registro registro) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("fecha", ExportacionDatosService.texto(registro.getFecha()));
        mapa.put("completado", registro.isCompletado());
        mapa.put("nota", registro.getNota());
        mapa.put("valoracion", registro.getValoracion());
        return mapa;
    }

    private Map<String, Object> datosCategoria(Categoria categoria) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("categoriaId", categoria.getCategoriaId());
        mapa.put("nombre", categoria.getNombre());
        mapa.put("descripcion", categoria.getDescripcion());
        mapa.put("color", categoria.getColor());
        mapa.put("icono", categoria.getIcono());
        return mapa;
    }
}
