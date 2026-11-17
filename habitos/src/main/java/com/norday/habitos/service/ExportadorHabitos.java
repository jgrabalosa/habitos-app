package com.norday.habitos.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ExportacionDatosService;
import com.norday.core.service.ExportadorDatosUsuario;
import com.norday.core.service.ZonaUsuarioService;
import com.norday.habitos.model.Categoria;
import com.norday.habitos.model.Habito;
import com.norday.habitos.model.HabitoObjetivo;
import com.norday.habitos.model.HistorialObjetivos;
import com.norday.habitos.model.HitoPagado;
import com.norday.habitos.model.Racha;
import com.norday.habitos.model.Registro;
import com.norday.habitos.model.Ritmo;
import com.norday.habitos.repository.ICategoriaDAO;
import com.norday.habitos.repository.IHabitoDAO;
import com.norday.habitos.repository.IHabitoObjetivoDAO;
import com.norday.habitos.repository.IHitoPagadoDAO;
import com.norday.habitos.repository.IRachaDAO;
import com.norday.habitos.repository.IRegistroDAO;
import com.norday.habitos.repository.IRitmoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aporta los datos del dominio "hábitos" a la exportación RGPD: hábitos,
 * rachas, ritmos, historial de objetivos, hitos cobrados, registros y
 * categorías personalizadas del usuario.
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
    private IRitmoDAO ritmoDAO;

    @Autowired
    private ICategoriaDAO categoriaDAO;

    @Autowired
    private IHabitoObjetivoDAO objetivoDAO;

    @Autowired
    private IHitoPagadoDAO hitoPagadoDAO;

    @Autowired
    private ObjetivoService objetivoService;

    @Autowired
    private ZonaUsuarioService zonaUsuarioService;

    @Override
    public String seccion() {
        return "habitos";
    }

    @Override
    public Map<String, Object> exportar(Usuario usuario) {
        List<Habito> habitos = habitoDAO.findByPropietario(usuario);
        // El objetivo vigente hoy, y no la copia del hábito si ha llegado un
        // cambio programado que todavía no se ha aplicado. Se calcula con el
        // historial, sin escribir: la exportación corre en una transacción de
        // solo lectura (ExportacionDatosService).
        LocalDate hoy = LocalDate.now(zonaUsuarioService.zonaDe(usuario));
        Map<Integer, HistorialObjetivos> historiales = objetivoService.historiales(habitos);

        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("habitos", habitos.stream()
                .map(h -> datosHabito(h, HistorialObjetivos.de(historiales, h).vistaEl(hoy)))
                .collect(Collectors.toList()));
        datos.put("categoriasPropias", categoriaDAO.findByCreador(usuario).stream()
                .map(this::datosCategoria)
                .collect(Collectors.toList()));
        return datos;
    }

    /** {@code deHoy} es la vista del hábito con el objetivo vigente hoy (no se guarda). */
    private Map<String, Object> datosHabito(Habito habito, Habito deHoy) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("habitoId", habito.getHabitoId());
        mapa.put("nombre", habito.getNombre());
        mapa.put("descripcion", habito.getDescripcion());
        mapa.put("frecuencia", deHoy.getFrecuencia().name());
        mapa.put("meta", deHoy.getMeta());
        mapa.put("unidad", deHoy.getUnidad().name());
        mapa.put("minutosObjetivo", deHoy.getMinutosObjetivo());
        mapa.put("diasSemana", deHoy.getDiasSemana());
        mapa.put("fechaInicio", ExportacionDatosService.texto(habito.getFechaInicio()));
        mapa.put("fechaCreacion", ExportacionDatosService.texto(habito.getFechaCreacion()));
        mapa.put("recordatorioActivo", habito.isRecordatorioActivo());
        mapa.put("recordatorioHora", ExportacionDatosService.texto(habito.getRecordatorioHora()));
        mapa.put("activo", habito.isActivo());
        mapa.put("categoria", habito.getTipo() == null ? null : habito.getTipo().getNombre());
        mapa.put("racha", datosRacha(rachaDAO.findByHabito(habito)));
        mapa.put("ritmo", datosRitmo(ritmoDAO.findByHabito(habito)));
        mapa.put("objetivos", objetivoDAO.findByHabito(habito).stream()
                .map(this::datosObjetivo)
                .collect(Collectors.toList()));
        mapa.put("hitosPagados", hitoPagadoDAO.findByHabito(habito).stream()
                .map(this::datosHitoPagado)
                .collect(Collectors.toList()));
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

    private Map<String, Object> datosRitmo(Ritmo ritmo) {
        if (ritmo == null) {
            return null;
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("valor", ritmo.getValor());
        mapa.put("desde", ExportacionDatosService.texto(ritmo.getDesde()));
        mapa.put("valorPrevio", ritmo.getValorPrevio());
        mapa.put("desdePrevio", ExportacionDatosService.texto(ritmo.getDesdePrevio()));
        mapa.put("cambio", ExportacionDatosService.texto(ritmo.getCambio()));
        return mapa;
    }

    private Map<String, Object> datosObjetivo(HabitoObjetivo objetivo) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("desde", ExportacionDatosService.texto(objetivo.getDesde()));
        mapa.put("unidad", objetivo.getUnidad().name());
        mapa.put("frecuencia", objetivo.getFrecuencia().name());
        mapa.put("meta", objetivo.getMeta());
        mapa.put("minutosObjetivo", objetivo.getMinutosObjetivo());
        mapa.put("diasSemana", objetivo.getDiasSemana());
        return mapa;
    }

    private Map<String, Object> datosHitoPagado(HitoPagado hito) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("umbral", hito.getUmbral());
        mapa.put("pagadoEn", ExportacionDatosService.texto(hito.getPagadoEn()));
        return mapa;
    }

    private Map<String, Object> datosRegistro(Registro registro) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("fecha", ExportacionDatosService.texto(registro.getFecha()));
        mapa.put("creadoEn", ExportacionDatosService.texto(registro.getCreadoEn()));
        mapa.put("completado", registro.isCompletado());
        mapa.put("nota", registro.getNota());
        mapa.put("valoracion", registro.getValoracion());
        mapa.put("cantidad", registro.getCantidad());
        mapa.put("origen", registro.getOrigen().name());
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
