package com.norday.conocimiento.service;

import com.norday.conocimiento.model.LecturaPildora;
import com.norday.conocimiento.model.RespuestaReto;
import com.norday.conocimiento.model.UsuarioCategoriaPreferencia;
import com.norday.conocimiento.model.UsuarioPildora;
import com.norday.conocimiento.model.UsuarioReto;
import com.norday.conocimiento.model.ValoracionPildora;
import com.norday.conocimiento.repository.ILecturaPildoraDAO;
import com.norday.conocimiento.repository.IRespuestaRetoDAO;
import com.norday.conocimiento.repository.IUsuarioCategoriaPreferenciaDAO;
import com.norday.conocimiento.repository.IUsuarioPildoraDAO;
import com.norday.conocimiento.repository.IUsuarioRetoDAO;
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
 * píldoras, preferencias de categoría, valoraciones y, desde V39, lecturas,
 * repasos de los retos y respuestas. No incluye `contenidoCompleto` ni las
 * opciones de los retos: son propiedad intelectual del proyecto, no dato
 * personal del usuario. Tampoco la clave de las lecturas y respuestas, que es
 * un detalle técnico de la app (como en la exportación de Focus).
 */
@Component
public class ExportadorConocimiento implements ExportadorDatosUsuario {

    @Autowired
    private IUsuarioPildoraDAO usuarioPildoraDAO;

    @Autowired
    private IUsuarioCategoriaPreferenciaDAO usuarioCategoriaPreferenciaDAO;

    @Autowired
    private IValoracionPildoraDAO valoracionPildoraDAO;

    @Autowired
    private ILecturaPildoraDAO lecturaPildoraDAO;

    @Autowired
    private IUsuarioRetoDAO usuarioRetoDAO;

    @Autowired
    private IRespuestaRetoDAO respuestaRetoDAO;

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
        int id = usuario.getUsuarioId();
        datos.put("lecturas", lecturaPildoraDAO.findByUsuario(id).stream()
                .map(this::datosLectura)
                .collect(Collectors.toList()));
        datos.put("repasos", usuarioRetoDAO.findByUsuario(id).stream()
                .map(this::datosRepaso)
                .collect(Collectors.toList()));
        datos.put("respuestas", respuestaRetoDAO.findByUsuario(id).stream()
                .map(this::datosRespuesta)
                .collect(Collectors.toList()));
        return datos;
    }

    private Map<String, Object> datosLectura(LecturaPildora lectura) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("titulo", lectura.getPildora().getTitulo());
        mapa.put("dia", ExportacionDatosService.texto(lectura.getDia()));
        mapa.put("leidaEn", ExportacionDatosService.texto(lectura.getLeidaEn()));
        mapa.put("premioMonedas", lectura.getPremioMonedas());
        mapa.put("premioXp", lectura.getPremioXp());
        mapa.put("premioLogros", lectura.getPremioLogros());
        return mapa;
    }

    private Map<String, Object> datosRepaso(UsuarioReto repaso) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("enunciado", repaso.getReto().getEnunciado());
        mapa.put("repasos", repaso.getRepasos());
        mapa.put("fallos", repaso.getFallos());
        mapa.put("estabilidad", repaso.getEstabilidad());
        mapa.put("dificultad", repaso.getDificultad());
        mapa.put("ultimoRepaso", ExportacionDatosService.texto(repaso.getUltimoRepaso()));
        mapa.put("venceEn", ExportacionDatosService.texto(repaso.getVenceEn()));
        return mapa;
    }

    private Map<String, Object> datosRespuesta(RespuestaReto respuesta) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("enunciado", respuesta.getReto().getEnunciado());
        mapa.put("opcion", respuesta.getOpcion());
        mapa.put("acierto", respuesta.isAcierto());
        mapa.put("dia", ExportacionDatosService.texto(respuesta.getDia()));
        mapa.put("respondidaEn", ExportacionDatosService.texto(respuesta.getRespondidaEn()));
        mapa.put("premioMonedas", respuesta.getPremioMonedas());
        mapa.put("premioXp", respuesta.getPremioXp());
        mapa.put("premioLogros", respuesta.getPremioLogros());
        return mapa;
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
