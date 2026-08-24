package com.norday.core.service;

import com.norday.core.exception.RecursoNoEncontradoException;
import com.norday.core.model.Usuario;
import com.norday.core.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Monta el volcado completo de los datos personales de un usuario para el
 * derecho de portabilidad del RGPD (art. 20).
 *
 * Nunca incluye la contraseña ni el token FCM: la primera no es un dato que
 * el titular necesite portar y el segundo es un identificador de dispositivo
 * que no aporta nada y sí riesgo si el archivo acaba donde no debe.
 */
@Service
public class ExportacionDatosService {

    @Autowired
    private IUsuarioDAO usuarioDAO;

    /** Spring recolecta aquí todas las implementaciones registradas. */
    @Autowired
    private List<ExportadorDatosUsuario> exportadores;

    @Transactional(readOnly = true)
    public Map<String, Object> exportar(int usuarioId) {
        Usuario usuario = usuarioDAO.findById(usuarioId);
        if (usuario == null) {
            throw new RecursoNoEncontradoException("Usuario no encontrado");
        }

        Map<String, Object> raiz = new LinkedHashMap<>();
        raiz.put("formato", "norday-exportacion-v1");
        raiz.put("generado", Instant.now().toString());
        raiz.put("cuenta", datosCuenta(usuario));

        for (ExportadorDatosUsuario exportador : exportadores) {
            raiz.put(exportador.seccion(), exportador.exportar(usuario));
        }

        return raiz;
    }

    private Map<String, Object> datosCuenta(Usuario usuario) {
        Map<String, Object> cuenta = new LinkedHashMap<>();
        cuenta.put("usuarioId", usuario.getUsuarioId());
        cuenta.put("nombre", usuario.getNombre());
        cuenta.put("username", usuario.getUsername());
        cuenta.put("email", usuario.getEmail());
        cuenta.put("proveedorAuth", usuario.getProveedorAuth());
        cuenta.put("idioma", usuario.getIdioma());
        cuenta.put("zonaHoraria", usuario.getZonaHoraria());
        cuenta.put("fechaRegistro", texto(usuario.getFechaRegistro()));
        return cuenta;
    }

    /** Fechas como texto ISO legible, tolerante a nulos. */
    public static String texto(Object valor) {
        return valor == null ? null : valor.toString();
    }
}
