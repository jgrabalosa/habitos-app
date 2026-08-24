package com.norday.gamificacion.service;

import com.norday.core.model.Usuario;
import com.norday.core.service.ExportacionDatosService;
import com.norday.core.service.ExportadorDatosUsuario;
import com.norday.gamificacion.model.Mascota;
import com.norday.gamificacion.model.UsuarioLogro;
import com.norday.gamificacion.model.UsuarioMoneda;
import com.norday.gamificacion.model.UsuarioProducto;
import com.norday.gamificacion.repository.IMascotaDAO;
import com.norday.gamificacion.repository.IUsuarioLogroDAO;
import com.norday.gamificacion.repository.IUsuarioMonedaDAO;
import com.norday.gamificacion.repository.IUsuarioProductoDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Aporta los datos de gamificación a la exportación RGPD: logros
 * conseguidos, movimientos de monedas, productos poseídos y mascota.
 */
@Component
public class ExportadorGamificacion implements ExportadorDatosUsuario {

    @Autowired
    private IUsuarioLogroDAO usuarioLogroDAO;

    @Autowired
    private IUsuarioMonedaDAO usuarioMonedaDAO;

    @Autowired
    private IUsuarioProductoDAO usuarioProductoDAO;

    @Autowired
    private IMascotaDAO mascotaDAO;

    @Override
    public String seccion() {
        return "gamificacion";
    }

    @Override
    public Map<String, Object> exportar(Usuario usuario) {
        Map<String, Object> datos = new LinkedHashMap<>();
        datos.put("logros", usuarioLogroDAO.findByUsuario(usuario).stream()
                .map(this::datosLogro)
                .collect(Collectors.toList()));
        datos.put("movimientosMonedas", usuarioMonedaDAO.findByUsuario(usuario).stream()
                .map(this::datosMovimiento)
                .collect(Collectors.toList()));
        datos.put("saldoMonedas", usuarioMonedaDAO.calcularSaldo(usuario.getUsuarioId()));
        datos.put("productos", usuarioProductoDAO.findByUsuario(usuario).stream()
                .map(this::datosProducto)
                .collect(Collectors.toList()));
        datos.put("mascota", datosMascota(mascotaDAO.findByUsuarioId(usuario.getUsuarioId())));
        return datos;
    }

    private Map<String, Object> datosLogro(UsuarioLogro usuarioLogro) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("codigo", usuarioLogro.getLogro().getCodigo());
        mapa.put("nombre", usuarioLogro.getLogro().getNombre());
        mapa.put("categoria", usuarioLogro.getLogro().getCategoria());
        mapa.put("fechaConseguido", ExportacionDatosService.texto(usuarioLogro.getFechaConseguido()));
        return mapa;
    }

    private Map<String, Object> datosMovimiento(UsuarioMoneda movimiento) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("cantidad", movimiento.getCantidad());
        mapa.put("origen", movimiento.getOrigen());
        mapa.put("descripcion", movimiento.getDescripcion());
        mapa.put("fecha", ExportacionDatosService.texto(movimiento.getFecha()));
        return mapa;
    }

    private Map<String, Object> datosProducto(UsuarioProducto usuarioProducto) {
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("codigo", usuarioProducto.getProducto().getCodigo());
        mapa.put("nombre", usuarioProducto.getProducto().getNombre());
        mapa.put("cantidad", usuarioProducto.getCantidad());
        mapa.put("equipado", usuarioProducto.isEquipado());
        mapa.put("fechaAdquirido", ExportacionDatosService.texto(usuarioProducto.getFechaAdquirido()));
        return mapa;
    }

    private Map<String, Object> datosMascota(Mascota mascota) {
        if (mascota == null) {
            return null;
        }
        Map<String, Object> mapa = new LinkedHashMap<>();
        mapa.put("nombre", mascota.getNombre());
        mapa.put("experiencia", mascota.getExperiencia());
        mapa.put("fechaUltimaComida", ExportacionDatosService.texto(mascota.getFechaUltimaComida()));
        mapa.put("fechaUltimoDiaCompleto", ExportacionDatosService.texto(mascota.getFechaUltimoDiaCompleto()));
        return mapa;
    }
}
