package com.norday.core.service;

import com.norday.core.repository.IUsuarioDAO;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Último acceso de cada usuario: mide quién abre la app, aunque no marque
 * hábitos. Se anota como mucho UNA vez al día (UTC) por usuario, en la primera
 * petición autenticada del día: el resto del día no toca la base de datos.
 *
 * El mapa vive en memoria. Si el servidor se reinicia se vacía y cada usuario
 * provoca como mucho una escritura más ese día: no es un problema.
 *
 * Nunca rompe una petición: cualquier fallo se captura y deja una línea de
 * log, con el mismo criterio que el email de bienvenida.
 *
 * Motor: no conoce ningún concepto de dominio.
 */
@Service
public class UltimoAccesoService {

    private static final org.slf4j.Logger log =
            org.slf4j.LoggerFactory.getLogger(UltimoAccesoService.class);

    @Autowired
    private IUsuarioDAO usuarioDAO;

    /** Último día (UTC) anotado por usuario. */
    private final ConcurrentHashMap<Integer, LocalDate> ultimoDiaAnotado = new ConcurrentHashMap<>();

    public void anotar(int usuarioId) {
        anotar(usuarioId, LocalDate.now(ZoneOffset.UTC));
    }

    /** Público solo para poder probar el cambio de día sin tocar el reloj. */
    public void anotar(int usuarioId, LocalDate hoy) {
        try {
            if (hoy.equals(ultimoDiaAnotado.get(usuarioId))) {
                return;
            }
            usuarioDAO.actualizarUltimoAcceso(usuarioId, LocalDateTime.now(ZoneOffset.UTC));
            // Solo se apunta el día si la escritura no falló: si falla, la
            // siguiente petición lo reintenta.
            ultimoDiaAnotado.put(usuarioId, hoy);
        } catch (Exception e) {
            log.warn("No se pudo anotar el último acceso del usuario {}: {}",
                    usuarioId, e.getMessage());
        }
    }
}
